package dev.zonely.whiteeffect.store;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public final class StorePurchaseService {
    public interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    public StorePurchaseService(Connections connections) { this.connections = connections; }

    public void initialize() throws SQLException {
        try (Connection c = connections.open(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ZonelyCoreStoreOrders (request_id CHAR(36) PRIMARY KEY,"
                    + " username VARCHAR(16) NOT NULL, total DECIMAL(18,2) NOT NULL, remaining DECIMAL(18,2) NOT NULL,"
                    + " created_at BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ZonelyCoreStoreDeliveries (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " request_id CHAR(36) NOT NULL, license_id INT NOT NULL, server_id INT NOT NULL, command_index INT NOT NULL,"
                    + " username VARCHAR(16) NOT NULL, command_text TEXT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',"
                    + " result_message VARCHAR(255), created_at BIGINT NOT NULL, processed_at BIGINT NOT NULL DEFAULT 0,"
                    + " UNIQUE KEY receipt_command (license_id,server_id,command_index),"
                    + " KEY pending_server (server_id,status,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    public Receipt buyProduct(UUID request, String username, int productId, int order, double quotedPrice,
                              int currentServerId, boolean sendToOtherServers)
            throws SQLException, Rejected {
        return buy(request, username, Collections.singletonList(new Selection(productId, order)), 0, quotedPrice,
                currentServerId, sendToOtherServers);
    }

    public Receipt buyBundle(UUID request, String username, int bundleId, double quotedPrice,
                             int currentServerId, boolean sendToOtherServers) throws SQLException, Rejected {
        return buy(request, username, null, bundleId, quotedPrice, currentServerId, sendToOtherServers);
    }

    private Receipt buy(UUID request, String username, List<Selection> selections, int bundleId, double quotedPrice,
                        int currentServerId, boolean sendToOtherServers)
            throws SQLException, Rejected {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}")) throw reject("invalid-player", "Invalid player name.");
        if (currentServerId <= 0) throw reject("receiver-not-ready", "Server connection token is not mapped.");
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                int userId;
                BigDecimal balance;
                try (PreparedStatement s = c.prepareStatement("SELECT id,credit FROM userslist WHERE nick=? FOR UPDATE")) {
                    s.setString(1, username);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw reject("web-user-missing", "Website account not found.");
                        userId = r.getInt(1); balance = r.getBigDecimal(2);
                    }
                }
                try (PreparedStatement s = c.prepareStatement("SELECT username,total,remaining FROM ZonelyCoreStoreOrders WHERE request_id=?")) {
                    s.setString(1, request.toString());
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) {
                            if (!username.equalsIgnoreCase(r.getString(1))) throw reject("invalid-request", "Order belongs to another player.");
                            Receipt receipt = new Receipt(r.getBigDecimal(2), r.getBigDecimal(3)); c.rollback(); return receipt;
                        }
                    }
                }
                BigDecimal total = BigDecimal.ZERO;
                if (bundleId > 0) {
                    try (PreparedStatement s = c.prepareStatement("SELECT products,price,expiry_at,productsStatus FROM productpackets WHERE id=? FOR UPDATE")) {
                        s.setInt(1, bundleId);
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next() || r.getInt(4) != 1) throw reject("bundle-disabled", "Bundle is unavailable.");
                            String expiry = r.getString(3);
                            if (expiry != null && !expiry.isEmpty() && LocalDate.parse(expiry.substring(0,10)).isBefore(LocalDate.now()))
                                throw reject("bundle-expired", "Bundle has expired.");
                            selections = bundleSelections(r.getString(1)); total = r.getBigDecimal(2);
                        }
                    }
                }
                List<Selection> ordered = new ArrayList<>(selections);
                ordered.sort(Comparator.comparingInt(x -> x.productId));
                List<Plan> plans = new ArrayList<>();
                for (Selection selection : ordered) {
                    Plan plan = prepare(c, userId, username, selection, currentServerId, sendToOtherServers);
                    plans.add(plan);
                    if (bundleId == 0) total = total.add(plan.price);
                }
                if (plans.isEmpty()) throw reject("empty-bundle", "Bundle has no deliverable products.");
                if (total.signum() < 0 || total.stripTrailingZeros().scale() > 0)
                    throw reject("invalid-price", "Price must be a non-negative whole number of credits.");
                if (total.compareTo(BigDecimal.valueOf(quotedPrice)) != 0)
                    throw reject("price-changed", "Price changed. Reopen the store before confirming.");
                if (balance.compareTo(total) < 0) throw reject("insufficient-credits", "Insufficient credits.");
                try (PreparedStatement s = c.prepareStatement("UPDATE userslist SET credit=credit-? WHERE id=?")) {
                    s.setBigDecimal(1,total); s.setInt(2,userId); s.executeUpdate();
                }
                BigDecimal remaining = balance.subtract(total);
                try (PreparedStatement s = c.prepareStatement("INSERT INTO ZonelyCoreStoreOrders VALUES (?,?,?,?,?)")) {
                    s.setString(1,request.toString()); s.setString(2,username); s.setBigDecimal(3,total);
                    s.setBigDecimal(4,remaining); s.setLong(5,System.currentTimeMillis()); s.executeUpdate();
                }
                for (int i=0;i<plans.size();i++) createLicense(c, request, userId, username, plans.get(i),
                        bundleId == 0 ? total : (i == 0 ? total : BigDecimal.ZERO));
                c.commit(); return new Receipt(total, remaining);
            } catch (JSONException e) {
                c.rollback();
                throw reject("invalid-product-data", "Product configuration is invalid. No credits were deducted.");
            } catch (SQLException | Rejected | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    private Plan prepare(Connection c, int userId, String username, Selection selection,
                         int currentServerId, boolean sendToOtherServers) throws SQLException, Rejected, JSONException {
        JSONArray variants;
        try (PreparedStatement s = c.prepareStatement("SELECT plugin_data FROM products WHERE id=? AND productType='minecraft' FOR UPDATE")) {
            s.setInt(1,selection.productId);
            try (ResultSet r=s.executeQuery()) {
                if (!r.next()) throw reject("product-missing", "Minecraft product not found.");
                variants = new JSONArray(r.getString(1));
            }
        }
        JSONObject variant=null;
        for (int i=0;i<variants.length();i++) {
            JSONObject candidate=variants.getJSONObject(i);
            if (candidate.optInt("order",-1)==selection.order) { variant=candidate; break; }
        }
        if (variant==null) throw reject("variant-missing", "Product option no longer exists.");
        JSONObject gui=variant.optJSONObject("zonely");
        if (!StoreVisibility.isVisible(gui)) throw reject("product-hidden", "This product is disabled in-game.");
        JSONArray required=variant.optJSONArray("required_products");
        if (required!=null && required.length()>0) {
            int owned=0;
            for(int i=0;i<required.length();i++) try (PreparedStatement s=c.prepareStatement(
                    "SELECT id FROM licenses WHERE userid=? AND productid=? AND suspendStatus=0 LIMIT 1")) {
                s.setInt(1,userId); s.setInt(2,required.getInt(i));
                try(ResultSet r=s.executeQuery()){if(r.next())owned++;}
            }
            if (variant.optInt("require_only_one",0)==1 ? owned==0 : owned<required.length())
                throw reject("prerequisite-missing", "Required products have not been purchased.");
        }
        List<Delivery> commands=new ArrayList<>();
        JSONArray servers=variant.optJSONArray("servers");
        Set<Integer> seenServers=new HashSet<>();
        if(servers!=null) for(int i=0;i<servers.length();i++) {
            JSONObject server=servers.getJSONObject(i);
            int id=server.optInt("id",server.optInt("server_id",0));
            if(id<=0 || !seenServers.add(id)) throw reject("invalid-server", "Invalid or duplicate target server.");
            if(!sendToOtherServers && id!=currentServerId) continue;
            try(PreparedStatement s=c.prepareStatement("SELECT id FROM plugin_servers WHERE id=?")) {
                s.setInt(1,id); try(ResultSet r=s.executeQuery()) {if(!r.next())throw reject("server-missing", "Target server no longer exists.");}
            }
            JSONArray list=server.optJSONArray("commands");
            if(list!=null) for(int j=0;j<list.length();j++) {
                Object item=list.get(j);
                String command=item instanceof JSONObject ? ((JSONObject)item).optString("cmd","") : String.valueOf(item);
                command=command.trim().replaceFirst("^/","")
                        .replace("%player_name%",username).replace("%player%",username).replace("{player}",username)
                        .replace("%username%",username).replace("%userid%",Integer.toString(userId)).replace("%user_id%",Integer.toString(userId));
                if(command.isEmpty())continue;
                if(command.length()>4096 || command.indexOf('\n')>=0 || command.indexOf('\r')>=0)
                    throw reject("invalid-command", "Invalid delivery command.");
                commands.add(new Delivery(id,j,command));
            }
        }
        if(commands.isEmpty()) throw reject("delivery-not-configured", "No delivery command is configured for an allowed target server. No credits were deducted.");
        boolean discounted=!"none".equalsIgnoreCase(variant.optString("discount_type","none"))
                && variant.optDouble("discounted_price",0)>0;
        String expiry=variant.optString("discount_expiry","");
        if(discounted && !expiry.isEmpty()) {
            try { if(LocalDateTime.parse(expiry.replace(' ','T')).isBefore(LocalDateTime.now()))discounted=false; }
            catch(java.time.format.DateTimeParseException ignored){discounted=false;}
        }
        if (!variant.has("price")) throw reject("invalid-price", "Product price is missing.");
        BigDecimal price=new BigDecimal(variant.optString(discounted?"discounted_price":"price","0"));
        if (price.signum() < 0) throw reject("invalid-price", "Product price cannot be negative.");
        if("limited".equalsIgnoreCase(variant.optString("stock_type","unlimited"))) {
            int stock=variant.optInt("stock",0);
            if(stock<=0)throw reject("out-of-stock", "Product is out of stock.");
            variant.put("stock",stock-1);
            try(PreparedStatement s=c.prepareStatement("UPDATE products SET plugin_data=? WHERE id=?")) {
                s.setString(1,variants.toString()); s.setInt(2,selection.productId); s.executeUpdate();
            }
        }
        return new Plan(selection,price,Math.max(0,variant.optInt("sale_duration",0)),commands);
    }

    private void createLicense(Connection c, UUID request, int userId, String username, Plan plan, BigDecimal paid) throws SQLException {
        LocalDateTime now=LocalDateTime.now();
        try(PreparedStatement s=c.prepareStatement("INSERT INTO lastedbuys (userid,productid,price,created_at) VALUES (?,?,?,?)")) {
            s.setInt(1,userId);s.setInt(2,plan.selection.productId);s.setBigDecimal(3,paid);s.setTimestamp(4,Timestamp.valueOf(now));s.executeUpdate();
        }
        int productLicense;
        try(PreparedStatement s=c.prepareStatement("INSERT INTO productlicenses (userid,productid,status,minecraft,created_at) VALUES (?,?,'0',?,?)",Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1,userId);s.setInt(2,plan.selection.productId);s.setInt(3,plan.selection.order);s.setTimestamp(4,Timestamp.valueOf(now));
            s.executeUpdate();productLicense=key(s);
        }
        int license;
        try(PreparedStatement s=c.prepareStatement("INSERT INTO licenses (productlicenses_id,userid,productid,domainAdress,licenseIP,suspendStatus,totalStart,created_at) VALUES (?,?,?,'','',0,1,?)",Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1,productLicense);s.setInt(2,userId);s.setInt(3,plan.selection.productId);s.setTimestamp(4,Timestamp.valueOf(now));
            s.executeUpdate();license=key(s);
        }
        try(PreparedStatement s=c.prepareStatement("INSERT INTO expiries (licenseID,expiry_at,startDate) VALUES (?,?,?)")) {
            s.setInt(1,license);s.setTimestamp(2,Timestamp.valueOf(plan.days>0?now.plusDays(plan.days):now));
            s.setTimestamp(3,Timestamp.valueOf(now));s.executeUpdate();
        }
        for(Delivery command:plan.commands) try(PreparedStatement s=c.prepareStatement(
                "INSERT INTO ZonelyCoreStoreDeliveries (request_id,license_id,server_id,command_index,username,command_text,created_at) VALUES (?,?,?,?,?,?,?)")) {
            s.setString(1,request.toString());s.setInt(2,productLicense);s.setInt(3,command.server);s.setInt(4,command.index);
            s.setString(5,username);s.setString(6,command.command);s.setLong(7,System.currentTimeMillis());s.executeUpdate();
        }
    }

    public static List<Selection> bundleSelections(String raw) throws Rejected {
        try { return parseBundleSelections(raw); }
        catch (JSONException e) { throw reject("invalid-bundle", "Invalid bundle configuration."); }
    }

    private static List<Selection> parseBundleSelections(String raw) throws Rejected, JSONException {
        List<Selection> result=new ArrayList<>();
        if(raw==null || raw.trim().isEmpty())return result;
        if(raw.trim().startsWith("[")) {
            JSONArray legacy=new JSONArray(raw);
            for(int i=0;i<legacy.length();i++)result.add(new Selection(legacy.getInt(i),0));
        } else {
            JSONObject object=new JSONObject(raw);
            for(String unsupported:Arrays.asList("skin_data","cosmetic_data","normal")) {
                JSONArray list=object.optJSONArray(unsupported);
                if(list!=null && list.length()>0)throw reject("web-only-bundle", "This bundle contains web-only products. Purchase it on the website.");
            }
            JSONArray plugins=object.optJSONArray("plugin_data");
            if(plugins!=null)for(int i=0;i<plugins.length();i++) {
                JSONObject item=plugins.getJSONObject(i);
                result.add(new Selection(item.getInt("product_id"),item.getInt("order")));
            }
        }
        return result;
    }
    private static int key(PreparedStatement s)throws SQLException {
        try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("License key missing");return r.getInt(1);}
    }
    private static Rejected reject(String code,String message){return new Rejected(code,message);}
    public static final class Rejected extends Exception {
        public final String code;
        Rejected(String code,String message){super(message);this.code=code;}
    }
    public static final class Receipt {
        public final BigDecimal total,remaining;
        Receipt(BigDecimal total,BigDecimal remaining){this.total=total;this.remaining=remaining;}
    }
    public static final class Selection {
        public final int productId,order;
        Selection(int productId,int order){this.productId=productId;this.order=order;}
    }
    private static final class Plan {
        final Selection selection;final BigDecimal price;final int days;final List<Delivery> commands;
        Plan(Selection selection,BigDecimal price,int days,List<Delivery> commands){this.selection=selection;this.price=price;this.days=days;this.commands=commands;}
    }
    private static final class Delivery {
        final int server,index;final String command;
        Delivery(int server,int index,String command){this.server=server;this.index=index;this.command=command;}
    }
}
