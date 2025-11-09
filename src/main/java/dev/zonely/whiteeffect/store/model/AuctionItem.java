package dev.zonely.whiteeffect.store.model;

import java.sql.Timestamp;

public class AuctionItem {
    private int id;
    private Integer rowOrder;
    private int serverId;
    private int creatorId;
    private int userId;
    private String material;
    private String customTitle;
    private String description;
    private String enchantments;
    private int durabilityCurrent;
    private int durabilityMax;
    private String itemData;
    private int creditCost;
    private boolean approved;
    private String buyerName;
    private int buyerUserId;
    private Timestamp createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getRowOrder() {
        return rowOrder;
    }

    public void setRowOrder(Integer rowOrder) {
        this.rowOrder = rowOrder;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle = customTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEnchantments() {
        return enchantments;
    }

    public void setEnchantments(String enchantments) {
        this.enchantments = enchantments;
    }

    public int getDurabilityCurrent() {
        return durabilityCurrent;
    }

    public void setDurabilityCurrent(int durabilityCurrent) {
        this.durabilityCurrent = durabilityCurrent;
    }

    public int getDurabilityMax() {
        return durabilityMax;
    }

    public void setDurabilityMax(int durabilityMax) {
        this.durabilityMax = durabilityMax;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public int getCreditCost() {
        return creditCost;
    }

    public void setCreditCost(int creditCost) {
        this.creditCost = creditCost;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public int getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(int buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
