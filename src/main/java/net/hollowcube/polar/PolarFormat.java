package net.hollowcube.polar;

public class PolarFormat {
    public static final PolarWriter WRITER = new PolarWriter();
    public static final PolarReader READER = new PolarReader();

    public static final int MAGIC_NUMBER = 0x506F6C72; // `Polr`
}
