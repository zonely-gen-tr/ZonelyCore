package dev.zonely.whiteeffect.store;

public class SaleManager {
    private static String lastBuyer = null;
    private static String lastProduct = "";

    public static void setSale(String buyer, String product) {
        lastBuyer = buyer;
        lastProduct = product;
    }

    public static void clearSale() {
        lastBuyer = null;
        lastProduct = "";
    }

    public static String getLastBuyer() {
        return lastBuyer;
    }
    public static String getLastProduct() {
        return lastProduct;
    }
}
