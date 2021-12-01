package com.cavetale.mapload;

import lombok.Value;

@Value
public final class Vec2 {
    public final int x;
    public final int z;

    @Override
    public String toString() {
        return x + "," + z;
    }
}
