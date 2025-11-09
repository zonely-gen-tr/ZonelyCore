package dev.zonely.whiteeffect.support;

import java.util.Objects;

final class SupportCategory {

    private final int id;
    private final String name;

    SupportCategory(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportCategory that = (SupportCategory) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SupportCategory{id=" + id + ", name='" + name + "'}";
    }
}

