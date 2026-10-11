package dev.zonely.whiteeffect.store;

import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.*;
import static org.junit.Assert.*;

public class StoreCheckoutIntegrationTest {
    private static String url;
    private static JSONObject fixture;
    private StorePurchaseService service;
    private StoreDeliveryRepository deliveries;
    private int product;
    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(url,"root","zonely_local_test_only");
    }
    @BeforeClass public static void schema() throws Exception {
        url=System.getenv("ZONELY_STORE_TEST_URL");
        Assume.assumeNotNull(url);
        if(!"jdbc:mariadb://127.0.0.1:33307/zonely_store_test".equals(url)) throw new IllegalArgumentException("Only the isolated loopback database is permitted");
        Class.forName("org.mariadb.jdbc.Driver");
        try(java.io.InputStream input=StoreCheckoutIntegrationTest.class.getResourceAsStream("/store-fixture.json")) {
            if(input==null)throw new IllegalStateException("Missing synthetic store fixture");
            java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int length;
            while((length=input.read(buffer))!=-1)data.write(buffer,0,length);
            fixture=new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));
        }
        try(Connection c=DriverManager.getConnection("jdbc:mariadb://127.0.0.1:33307/","root","zonely_local_test_only");Statement s=c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS zonely_store_test CHARACTER SET utf8mb4");
        }
        try(Connection c=connection();Statement s=c.createStatement()) {
            JSONObject schemas=fixture.getJSONObject("schemas");
            for(String table:Arrays.asList("userslist","products","productcategories","productpackets","plugin_servers","productlicenses","licenses","lastedbuys","expiries")) {
                s.executeUpdate("DROP TABLE IF EXISTS `"+table+"`");
                s.executeUpdate(schemas.getString(table));
            }
        }
        new StorePurchaseService(StoreCheckoutIntegrationTest::connection).initialize();
    }
    @Before public void seed() throws Exception {
        service=new StorePurchaseService(StoreCheckoutIntegrationTest::connection);
        deliveries=new StoreDeliveryRepository(StoreCheckoutIntegrationTest::connection);
        try(Connection c=connection();Statement s=c.createStatement()) {
            s.executeUpdate("DROP TRIGGER IF EXISTS test_checkout_failure");
            for(String table:Arrays.asList("ZonelyCoreStoreDeliveries","ZonelyCoreStoreOrders","expiries","licenses","productlicenses","lastedbuys","userslist","plugin_servers","products","productcategories","productpackets")) s.executeUpdate("DELETE FROM `"+table+"`");
            s.execute("SET SESSION sql_mode=''");
            for(String table:Arrays.asList("products","productcategories","productpackets")) {
                JSONArray rows=fixture.getJSONArray(table);
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);
                    List<String> keys=new ArrayList<>();
                    for(Iterator<?> names=row.keys();names.hasNext();) keys.add(String.valueOf(names.next()));
                    String columns="`"+String.join("`,`",keys)+"`";
                    try(PreparedStatement insert=c.prepareStatement("INSERT INTO `"+table+"` ("+columns+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")")) {
                        for(int k=0;k<keys.size();k++)insert.setObject(k+1,row.isNull(keys.get(k))?null:row.get(keys.get(k)));
                        insert.executeUpdate();
                    }
                }
            }
            s.executeUpdate("INSERT INTO userslist (id,nick,password,credit) VALUES (9001,'StoreTester','local-only',1000),(9002,'OtherTester','local-only',1000)");
            s.executeUpdate("INSERT INTO plugin_servers (id,userid,name,plugin_id,ip,port,password) VALUES (701,9001,'Local lobby',1,'127.0.0.1','25607','local-lobby-token'),(702,9001,'Local survival',1,'127.0.0.1','25608','local-survival-token')");
            try(ResultSet r=s.executeQuery("SELECT id FROM products WHERE productType='minecraft' AND plugin_data LIKE '[%' LIMIT 1")) {assertTrue(r.next());product=r.getInt(1);}
        }
        JSONObject variant=variant();
        variant.put("order",17).put("price","100").put("discount_type","none").put("stock_type","limited").put("stock",3).put("required_products",new JSONArray());
        variant.put("zonely",new JSONObject().put("showInGame",1).put("title","&eÖrnek Spawner"));
        variant.put("servers",new JSONArray()
                .put(new JSONObject().put("id",701).put("commands",new JSONArray().put(new JSONObject().put("cmd","/say Ürün %player% %userid%"))))
                .put(new JSONObject().put("server_id",702).put("commands",new JSONArray().put(new JSONObject().put("cmd","say Teslim %player_name%")))));
        writeVariants(new JSONArray().put(variant));
    }
    private JSONObject variant() throws Exception {
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT plugin_data FROM products WHERE id=?")) {
            s.setInt(1,product);try(ResultSet r=s.executeQuery()){assertTrue(r.next());return new JSONArray(r.getString(1)).getJSONObject(0);}
        }
    }
    private void writeVariants(JSONArray variants) throws Exception {
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("UPDATE products SET plugin_data=? WHERE id=?")) {
            s.setString(1,variants.toString());s.setInt(2,product);s.executeUpdate();
        }
    }
    private int scalar(String sql) throws Exception {
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}
    }
    private void rejected(String code,double quote) throws Exception {
        try {service.buyProduct(UUID.randomUUID(),"StoreTester",product,17,quote,701,true);fail("Expected rejection");}
        catch(StorePurchaseService.Rejected e){assertEquals(code,e.code);}
        assertEquals(1000,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreOrders"));
        assertEquals(3,variant().getInt("stock"));
    }
    @Test public void checkoutIsAtomicAndIdempotent() throws Exception {
        assertEquals(2,scalar("SELECT COUNT(*) FROM products"));
        UUID request=UUID.randomUUID();
        assertEquals(900,service.buyProduct(request,"StoreTester",product,17,100,701,true).remaining.intValueExact());
        service.buyProduct(request,"StoreTester",product,17,100,701,true);
        assertEquals(900,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM licenses"));
        assertEquals(17,scalar("SELECT minecraft FROM productlicenses"));
        assertEquals(2,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
        assertEquals(2,variant().getInt("stock"));
        List<StoreDeliveryRepository.Delivery> local=deliveries.claim(701,Collections.singleton("storetester"),true,10);
        assertEquals(1,local.size());assertEquals("say Ürün StoreTester 9001",local.get(0).command);
        deliveries.complete(local.get(0).id,"DONE","test console dispatch");
        assertTrue(deliveries.claim(701,Collections.singleton("storetester"),true,10).isEmpty());
        assertEquals(1,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries WHERE server_id=702 AND status='PENDING'"));
    }
    @Test public void missingCommandsDoNotCharge() throws Exception {
        writeVariants(new JSONArray().put(variant().put("servers",new JSONArray())));
        rejected("delivery-not-configured",100);
    }
    @Test public void changedPriceDoesNotChargeOrConsumeStock() throws Exception {rejected("price-changed",99);}
    @Test public void lateSqlFailureRollsBackPaymentStockAndLicenses() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TRIGGER test_checkout_failure BEFORE INSERT ON expiries FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Local rollback test'");
        }
        try {service.buyProduct(UUID.randomUUID(),"StoreTester",product,17,100,701,true);fail("Expected SQL failure");}
        catch(SQLException expected) {assertEquals("45000",expected.getSQLState());}
        finally {try(Connection c=connection();Statement s=c.createStatement()){s.executeUpdate("DROP TRIGGER test_checkout_failure");}}
        assertEquals(1000,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(3,variant().getInt("stock"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM productlicenses"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
    }
    @Test public void concurrentBuyersCannotOversellLastUnit() throws Exception {
        writeVariants(new JSONArray().put(variant().put("stock",1)));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String name:Arrays.asList("StoreTester","OtherTester")) results.add(pool.submit(() -> {
                try { service.buyProduct(UUID.randomUUID(),name,product,17,100,701,true);return true; }
                catch(StorePurchaseService.Rejected e){assertEquals("out-of-stock",e.code);return false;}
            }));
            assertNotEquals(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(1900,scalar("SELECT SUM(credit) FROM userslist"));
        assertEquals(0,variant().getInt("stock"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreOrders"));
    }
    @Test public void bundleUsesExplicitVariantOrdersAndChargesOnce() throws Exception {
        JSONObject first=variant(),second=new JSONObject(first.toString()).put("order",23).put("price",200);
        writeVariants(new JSONArray().put(first).put(second));
        JSONObject selections=new JSONObject().put("plugin_data",new JSONArray()
                .put(new JSONObject().put("product_id",product).put("order",17))
                .put(new JSONObject().put("product_id",product).put("order",23)));
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO productpackets (id,userid,name,products,price,productsStatus,expiry_at) VALUES (999,9001,'Local bundle',?,250,1,'2099-01-01')")) {
            s.setString(1,selections.toString());s.executeUpdate();
        }
        service.buyBundle(UUID.randomUUID(),"StoreTester",999,250,701,true);
        assertEquals(750,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(40,scalar("SELECT SUM(minecraft) FROM productlicenses"));
        assertEquals(250,scalar("SELECT SUM(price) FROM lastedbuys"));
        assertEquals(4,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
    }
    @Test public void offlineDeliveryWaitsAndTwoReceiversCannotClaimSameCommand() throws Exception {
        service.buyProduct(UUID.randomUUID(),"StoreTester",product,17,100,701,true);
        assertEquals(701,deliveries.resolveServer("local-lobby-token"));
        assertEquals(702,deliveries.resolveServer("local-survival-token"));
        assertTrue(deliveries.claim(701,Collections.emptySet(),true,10).isEmpty());
        assertTrue(deliveries.claim(999,Collections.emptySet(),false,10).isEmpty());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<List<StoreDeliveryRepository.Delivery>> a=pool.submit(()->deliveries.claim(701,Collections.emptySet(),false,10));
            Future<List<StoreDeliveryRepository.Delivery>> b=pool.submit(()->deliveries.claim(701,Collections.emptySet(),false,10));
            assertEquals(1,a.get(10,TimeUnit.SECONDS).size()+b.get(10,TimeUnit.SECONDS).size());
        } finally {pool.shutdownNow();}
    }

    @Test public void localOnlyCheckoutNeverQueuesOtherServers() throws Exception {
        UUID request=UUID.randomUUID();
        service.buyProduct(request,"StoreTester",product,17,100,701,false);
        service.buyProduct(request,"StoreTester",product,17,100,701,true);
        assertEquals(900,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
        assertEquals(701,scalar("SELECT server_id FROM ZonelyCoreStoreDeliveries"));
        assertEquals(2,variant().getInt("stock"));
    }

    @Test public void localOnlyRemoteProductDoesNotCharge() throws Exception {
        JSONObject option=variant();
        option.put("servers",new JSONArray().put(option.getJSONArray("servers").getJSONObject(1)));
        writeVariants(new JSONArray().put(option));
        try {service.buyProduct(UUID.randomUUID(),"StoreTester",product,17,100,701,false);fail("Expected rejection");}
        catch(StorePurchaseService.Rejected e){assertEquals("delivery-not-configured",e.code);}
        assertEquals(1000,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(3,variant().getInt("stock"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM licenses"));
    }

    @Test public void localOnlyBundleRollsBackWhenOneProductHasNoLocalCommand() throws Exception {
        JSONObject first=variant(),second=new JSONObject(first.toString()).put("order",23);
        second.put("servers",new JSONArray().put(second.getJSONArray("servers").getJSONObject(1)));
        writeVariants(new JSONArray().put(first).put(second));
        JSONObject selections=new JSONObject().put("plugin_data",new JSONArray()
                .put(new JSONObject().put("product_id",product).put("order",17))
                .put(new JSONObject().put("product_id",product).put("order",23)));
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO productpackets (id,userid,name,products,price,productsStatus,expiry_at) VALUES (999,9001,'Local bundle',?,200,1,'2099-01-01')")) {
            s.setString(1,selections.toString());s.executeUpdate();
        }
        try {service.buyBundle(UUID.randomUUID(),"StoreTester",999,200,701,false);fail("Expected rejection");}
        catch(StorePurchaseService.Rejected e){assertEquals("delivery-not-configured",e.code);}
        assertEquals(1000,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(3,variant().getInt("stock"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreOrders"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM licenses"));
    }

    @Test public void tokenMustIdentifyExactlyOnePanelServer() throws Exception {
        assertEquals(0,deliveries.resolveServer(null));
        assertEquals(0,deliveries.resolveServer(""));
        assertEquals(0,deliveries.resolveServer("unknown-token"));
        assertEquals(701,deliveries.resolveServer("local-lobby-token"));
        try(Connection c=connection();Statement s=c.createStatement()) {
            s.executeUpdate("UPDATE plugin_servers SET password='local-lobby-token' WHERE id=702");
        }
        assertEquals(0,deliveries.resolveServer("local-lobby-token"));
    }

    @Test public void booleanPanelVisibilityCanBePurchased() throws Exception {
        JSONObject option=variant();
        option.getJSONObject("zonely").put("showInGame",true);
        writeVariants(new JSONArray().put(option));
        service.buyProduct(UUID.randomUUID(),"StoreTester",product,17,100,701,false);
        assertEquals(900,scalar("SELECT credit FROM userslist WHERE id=9001"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM ZonelyCoreStoreDeliveries"));
    }

    @Test public void hiddenProductCannotBePurchasedDirectly() throws Exception {
        JSONObject option=variant();
        option.getJSONObject("zonely").put("showInGame",false);
        writeVariants(new JSONArray().put(option));
        rejected("product-hidden",100);
    }

    @Test public void creditLeaderboardsWorkWithStrictGrouping() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lastcredits (id INT AUTO_INCREMENT PRIMARY KEY,userid INT NOT NULL,gain INT NOT NULL,created_at DATETIME NOT NULL) ENGINE=InnoDB");
            s.executeUpdate("DELETE FROM lastcredits");
            s.executeUpdate("INSERT INTO lastcredits (userid,gain,created_at) VALUES (9001,100,NOW()),(9001,200,NOW()),(9002,50,DATE_SUB(NOW(),INTERVAL 2 YEAR))");
            s.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES,ONLY_FULL_GROUP_BY'");
            for(LastCreditsMenuManager.Category category:LastCreditsMenuManager.Category.values()) {
                try(PreparedStatement query=c.prepareStatement(LastCreditQueries.leaderboard(category))) {
                    query.setInt(1,6);query.setInt(2,0);
                    try(ResultSet r=query.executeQuery()) {
                        assertTrue(category.name(),r.next());
                        if(category!=LastCreditsMenuManager.Category.RECENT) {
                            assertEquals("StoreTester",r.getString("username"));assertEquals(300,r.getLong("total"));
                            assertEquals(category==LastCreditsMenuManager.Category.ALL_TIME,r.next());
                        }
                    }
                }
            }
        }
    }

    @Test public void recentCreditsReturnSixRowsAndSeeDatabaseChanges() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lastcredits (id INT AUTO_INCREMENT PRIMARY KEY,userid INT NOT NULL,gain INT NOT NULL,created_at DATETIME NOT NULL) ENGINE=InnoDB");
            s.executeUpdate("DELETE FROM lastcredits");
            for(int i=1;i<=6;i++)s.executeUpdate("INSERT INTO lastcredits (userid,gain,created_at) VALUES (9001,"+i+",DATE_SUB(NOW(),INTERVAL "+(10-i)+" MINUTE))");
            try(PreparedStatement query=c.prepareStatement(LastCreditQueries.leaderboard(LastCreditsMenuManager.Category.RECENT))) {
                query.setInt(1,6);query.setInt(2,0);
                try(ResultSet r=query.executeQuery()){int count=0;while(r.next())count++;assertEquals(6,count);}
                s.executeUpdate("INSERT INTO lastcredits (userid,gain,created_at) VALUES (9002,9999,NOW())");
                try(ResultSet r=query.executeQuery()){assertTrue(r.next());assertEquals("OtherTester",r.getString("username"));assertEquals(9999,r.getLong("total"));}
            }
        }
    }
}
