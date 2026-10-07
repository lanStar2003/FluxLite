package com.fluxlite.gui.preview;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fluxlite.core.PortRole;
import com.fluxlite.core.PortStatus;
import com.fluxlite.core.alert.Alert;
import com.fluxlite.gui.ConnectorScreen;
import com.fluxlite.gui.ControlCenterScreen;
import com.fluxlite.gui.ui.Host;
import com.fluxlite.gui.ui.UiScreen;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Longs;

/**
 * Renders the screens with sample data to build/previews/*.png, using Minecraft's own font. Skipped when the vanilla
 * jar is not in the Gradle cache.
 */
class PreviewTest {

    private static final File JAR = new File(
        System.getProperty("user.home"),
        ".gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar");
    private static final File OUT = new File("build/previews");

    static final class FakeHost implements Host {

        final Properties lang = new Properties();

        FakeHost() throws Exception {
            try (Reader r = new InputStreamReader(
                PreviewTest.class.getResourceAsStream("/assets/fluxlite/lang/zh_CN.lang"),
                StandardCharsets.UTF_8)) {
                lang.load(r);
            }
        }

        @Override
        public void send(int kind, NBTTagCompound data) {}

        @Override
        public void close() {}

        @Override
        public String tr(String key, Object... args) {
            String v = lang.getProperty(key, key);
            for (int i = 0; i < args.length; i++)
                if (args[i] instanceof String s && s.startsWith("@L:")) args[i] = lang.getProperty(s.substring(3), s);
            return args.length == 0 ? v : String.format(v, args);
        }

        @Override
        public void highlight(int dim, int x, int y, int z) {}
    }

    private static void render(UiScreen screen, String name, int w, int h, int scale, int mx, int my) throws Exception {
        try (ZipFile jar = new ZipFile(JAR)) {
            Java2DCanvas c = new Java2DCanvas(w, h, scale, jar);
            c.backdrop();
            screen.frame(c, mx, my, w, h, 0);
            OUT.mkdirs();
            ImageIO.write(c.image, "png", new File(OUT, name + ".png"));
        }
    }

    // ------------------------------------------------------------------ sample data

    private static NBTTagCompound face(int side, byte vis, PortRole role, String target, long v, long in, long out) {
        NBTTagCompound f = new NBTTagCompound();
        f.setByte("side", (byte) side);
        f.setByte("vis", vis);
        f.setByte("role", (byte) role.ordinal());
        f.setByte("status", (byte) (vis == TileConnector.VIS_OFF ? PortStatus.DISABLED : PortStatus.OK).ordinal());
        f.setString("target", target);
        f.setLong("v", v);
        f.setLong("in", in);
        f.setLong("out", out);
        return f;
    }

    static NBTTagCompound connectorData() {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("ok", true);
        t.setString("name", "主基地 · 聚变区");
        t.setString("owner", "lan");
        t.setInteger("chunk", 1);
        t.setLong("in", 8_388_608);
        t.setLong("out", 2_097_152);
        NBTTagList l = new NBTTagList();
        l.appendTag(face(0, TileConnector.VIS_NONE, PortRole.NONE, "", 0, 0, 0));
        l.appendTag(face(1, TileConnector.VIS_OUT, PortRole.OUTPUT, "UV能源仓", 524_288, 0, 1_048_576));
        l.appendTag(face(2, TileConnector.VIS_IN, PortRole.INPUT, "UHV动力仓", 2_097_152, 8_388_608, 0));
        l.appendTag(face(3, TileConnector.VIS_BOTH, PortRole.BOTH, "超导线缆 ×42", 524_288, 0, 1_048_576));
        l.appendTag(face(4, TileConnector.VIS_OFF, PortRole.NONE, "LuV电池箱", 0, 0, 0));
        l.appendTag(face(5, TileConnector.VIS_NONE, PortRole.NONE, "", 0, 0, 0));
        t.setTag("faces", l);
        return t;
    }

    private static long[] wave(int n, double base, double amp, double period, long seed) {
        long[] v = new long[n];
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < n; i++)
            v[i] = (long) Math.max(0, base + amp * Math.sin(i / period) + amp * 0.35 * (r.nextDouble() - 0.5));
        return v;
    }

    private static void common(NBTTagCompound t, int page) {
        t.setBoolean("ok", true);
        t.setInteger("page", page);
        t.setString("detail", "");
        t.setString("teamName", "lan");
        t.setString("team", "00000000-0000-0000-0000-000000000000");
        t.setInteger("online", 7);
        t.setInteger("connectors", 8);
        t.setInteger("alerts", 2);
    }

    private static NBTTagCompound top(String name, long v) {
        NBTTagCompound e = new NBTTagCompound();
        e.setString("k", "p:1:" + name.length());
        e.setString("n", name);
        e.setLong("v", v);
        return e;
    }

    static NBTTagCompound overview() {
        NBTTagCompound t = new NBTTagCompound();
        common(t, 0);
        t.setLong("in", 12_582_912);
        t.setLong("out", 9_437_184);
        t.setString("balance", "184467440737095516");
        t.setLong("eta", -1);
        t.setIntArray("cin", Longs.pack(wave(200, 12_000_000, 1_500_000, 9, 1)));
        t.setIntArray("cout", Longs.pack(wave(200, 9_000_000, 2_500_000, 5, 2)));
        NBTTagList out = new NBTTagList();
        out.appendTag(top("大型化学反应釜", 4_194_304));
        out.appendTag(top("装配线", 2_097_152));
        out.appendTag(top("电弧炉阵列", 1_048_576));
        t.setTag("topOut", out);
        NBTTagList in = new NBTTagList();
        in.appendTag(top("聚变反应堆 MK3", 8_388_608));
        in.appendTag(top("大型燃气涡轮", 3_145_728));
        in.appendTag(top("太阳能阵列", 1_048_576));
        t.setTag("topIn", in);
        return t;
    }

    private static NBTTagCompound row(String key, String name, String conn, int role, long v, long ni, long no, long pk,
        String tot, int kind, int side, int status) {
        NBTTagCompound r = new NBTTagCompound();
        r.setString("k", key);
        r.setString("n", name);
        r.setString("c", conn);
        r.setByte("r", (byte) role);
        r.setLong("v", v);
        r.setLong("ni", ni);
        r.setLong("no", no);
        r.setLong("pk", pk);
        r.setString("tot", tot);
        r.setBoolean("s", kind == 2);
        r.setBoolean("on", status != 2);
        r.setByte("st", (byte) status);
        r.setByte("kd", (byte) kind);
        r.setByte("sd", (byte) side);
        return r;
    }

    private static NBTTagCompound conn(long id, String name, boolean online) {
        NBTTagCompound e = new NBTTagCompound();
        e.setLong("id", id);
        e.setString("n", name);
        e.setBoolean("on", online);
        return e;
    }

    static NBTTagCompound devices() {
        NBTTagCompound t = new NBTTagCompound();
        common(t, 1);
        NBTTagList l = new NBTTagList();
        l.appendTag(row("a", "聚变反应堆 MK3 动力仓", "聚变区", 1, 2_097_152, 8_388_608, 0, 8_388_608, "93422347665408", 0, 2, 0));
        l.appendTag(row("b", "大型化学反应釜 能源仓", "化工区", 2, 524_288, 0, 4_194_304, 4_194_304, "12093847562", 0, 5, 0));
        l.appendTag(row("c", "超导线缆 ×42", "主基地", 3, 524_288, 1_048_576, 2_097_152, 3_145_728, "9384756201", 1, 1, 0));
        l.appendTag(row("d", "装配线", "主基地", 2, 131_072, 0, 2_097_152, 2_097_152, "829384756", 2, 1, 0));
        l.appendTag(row("e", "大型燃气涡轮", "发电站", 1, 32_768, 3_145_728, 0, 3_145_728, "77665544332", 0, 4, 0));
        l.appendTag(row("f", "电弧炉阵列", "冶炼区", 2, 32_768, 0, 0, 1_048_576, "4433221100", 0, 3, 1));
        l.appendTag(row("g", "真空冷冻机", "冶炼区", 2, 8_192, 0, 491_520, 491_520, "993827465", 2, 3, 0));
        l.appendTag(row("h", "太阳能阵列", "屋顶", 1, 2_048, 0, 0, 1_048_576, "120938475", 0, 1, 2));
        l.appendTag(row("i", "LuV电池箱", "主基地", 0, 0, 0, 0, 0, "0", 0, 4, 3));
        l.appendTag(row("j", "量子箱", "电子区", 0, 0, 0, 0, 0, "0", 0, 0, 4));
        t.setTag("rows", l);
        t.setInteger("count", 23);
        t.setInteger("all", 23);
        t.setInteger("offset", 0);
        t.setLong("sumIn", 12_582_912);
        t.setLong("sumOut", 9_437_184);
        t.setIntArray("statusCount", new int[] { 15, 4, 2, 1, 1 });
        t.setIntArray("tiers", new int[] { 1, 3, 5, 6, 7, 8, 9 });
        NBTTagList conns = new NBTTagList();
        conns.appendTag(conn(1, "主基地", true));
        conns.appendTag(conn(2, "聚变区", true));
        conns.appendTag(conn(3, "化工区", true));
        conns.appendTag(conn(4, "冶炼区", true));
        conns.appendTag(conn(5, "发电站", true));
        conns.appendTag(conn(6, "电子区", true));
        conns.appendTag(conn(7, "屋顶", false));
        t.setTag("conns", conns);
        return t;
    }

    /** The device list narrowed to running machines. */
    static NBTTagCompound devicesFiltered() {
        NBTTagCompound t = devices();
        NBTTagList all = t.getTagList("rows", 10), l = new NBTTagList();
        for (int i = 0; i < all.tagCount(); i++) if (all.getCompoundTagAt(i)
            .getByte("st") == 0) l.appendTag(all.getCompoundTagAt(i));
        t.setTag("rows", l);
        t.setInteger("count", 15);
        return t;
    }

    static NBTTagCompound detail() {
        NBTTagCompound t = new NBTTagCompound();
        common(t, 1);
        t.setString("detail", "p:1:2");
        t.setString("name", "大型化学反应釜 能源仓");
        t.setString("conn", "化工区");
        t.setInteger("side", 2);
        t.setIntArray("pos", new int[] { -1204, 64, 388 });
        t.setInteger("dim", 0);
        t.setBoolean("online", true);
        t.setByte("role", (byte) 2);
        t.setLong("v", 524_288);
        t.setLong("a", 16);
        t.setLong("ni", 0);
        t.setLong("no", 4_194_304);
        t.setIntArray("cin", Longs.pack(new long[200]));
        long[] o = new long[200];
        for (int i = 0; i < 200; i++) o[i] = (i / 25) % 2 == 0 ? 4_194_304 : 524_288;
        t.setIntArray("cout", Longs.pack(o));
        t.setLong("peak", 8_388_608);
        t.setLong("peakAgo", 1_380);
        t.setLong("duty", 873);
        t.setLong("sat", 1000);
        t.setString("total", "12093847562000");
        t.setString("today", "1209384756200");
        long[] hod = new long[24];
        for (int i = 0; i < 24; i++) hod[i] = (long) (2_000_000 + 1_500_000 * Math.sin((i - 6) / 24.0 * Math.PI * 2));
        t.setIntArray("hod", Longs.pack(hod));
        return t;
    }

    static NBTTagCompound alerts(boolean any) {
        NBTTagCompound t = new NBTTagCompound();
        common(t, 2);
        NBTTagList l = new NBTTagList();
        if (any) {
            l.appendTag(new Alert(Alert.Type.ETA_SHORT, 0, -1, "8m 20s").write());
            l.appendTag(new Alert(Alert.Type.UNDER_SUPPLY, 3, 2, "装配线 能源仓", "@L:fluxlite.side.2", "72%").write());
        } else t.setInteger("alerts", 0);
        t.setTag("alertList", l);
        t.setBoolean("chat", true);
        t.setBoolean("redstone", false);
        t.setBoolean("ccOwner", true);
        t.setBoolean("holo", true);
        t.setBoolean("holoAllowed", true);
        return t;
    }

    // ------------------------------------------------------------------ renders

    private static Map<String, int[]> sizes() {
        Map<String, int[]> m = new HashMap<>();
        m.put("1080p", new int[] { 480, 270, 4 });
        m.put("large", new int[] { 640, 360, 3 });
        return m;
    }

    @Test
    void renderAll() throws Exception {
        Assumptions.assumeTrue(JAR.isFile(), "vanilla client jar not found");
        FakeHost host = new FakeHost();
        for (Map.Entry<String, int[]> e : sizes().entrySet()) {
            int w = e.getValue()[0], h = e.getValue()[1], s = e.getValue()[2];
            String sz = e.getKey();

            ConnectorScreen cs = new ConnectorScreen(0, 0, 0, 0);
            cs.attach(host);
            cs.onData(connectorData());
            render(cs, "connector_" + sz, w, h, s, w / 2 + 70, h / 2 + 40);

            ControlCenterScreen cc = new ControlCenterScreen(0, 0, 0, 0);
            cc.attach(host);
            cc.onData(overview());
            render(cc, "cc_overview_" + sz, w, h, s, -1, -1);

            ControlCenterScreen dev = new ControlCenterScreen(0, 0, 0, 0);
            dev.attach(host);
            select(dev, 1);
            dev.onData(devices());
            render(dev, "cc_devices_" + sz, w, h, s, w / 2, h / 2 + 10);

            ControlCenterScreen menu = new ControlCenterScreen(0, 0, 0, 0);
            menu.attach(host);
            select(menu, 1);
            setInt(menu, "statusFilter", 0);
            menu.onData(devicesFiltered());
            menu.openMenu("status");
            render(menu, "cc_devices_menu_" + sz, w, h, s, w / 2 - 150, h / 2 - 50);

            ControlCenterScreen det = new ControlCenterScreen(0, 0, 0, 0);
            det.attach(host);
            select(det, 1);
            openDetail(det, "p:1:2");
            det.onData(detail());
            render(det, "cc_detail_" + sz, w, h, s, -1, -1);

            ControlCenterScreen al = new ControlCenterScreen(0, 0, 0, 0);
            al.attach(host);
            select(al, 2);
            al.onData(alerts(true));
            render(al, "cc_alerts_" + sz, w, h, s, -1, -1);

            ControlCenterScreen ok = new ControlCenterScreen(0, 0, 0, 0);
            ok.attach(host);
            select(ok, 2);
            ok.onData(alerts(false));
            render(ok, "cc_alerts_ok_" + sz, w, h, s, -1, -1);
        }
    }

    private static void select(ControlCenterScreen s, int tab) throws Exception {
        java.lang.reflect.Field f = ControlCenterScreen.class.getDeclaredField("tab");
        f.setAccessible(true);
        f.setInt(s, tab);
    }

    private static void setInt(ControlCenterScreen s, String field, int v) throws Exception {
        java.lang.reflect.Field f = ControlCenterScreen.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(s, v);
    }

    private static void openDetail(ControlCenterScreen s, String key) throws Exception {
        java.lang.reflect.Field f = ControlCenterScreen.class.getDeclaredField("detailKey");
        f.setAccessible(true);
        f.set(s, key);
    }
}
