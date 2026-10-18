package com.ringsofiron.combatsys.defs;

import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.moddingdata.DefRegistry;

/**
 * In-memory registry of weapon, projectile, missile and warhead defs by id. modding-data fills
 * it from TOML; {@link #createDefault()} holds the illustrative entries from the design so the
 * module runs and tests on its own.
 */
public class CombatDefs {
    public final ObjectMap<String, WeaponDef> weapons = new ObjectMap<>();
    public final ObjectMap<String, ProjectileDef> projectiles = new ObjectMap<>();
    public final ObjectMap<String, MissileDef> missiles = new ObjectMap<>();
    public final ObjectMap<String, WarheadDef> warheads = new ObjectMap<>();

    public WeaponDef weapon(String id) {
        return require(weapons.get(id), "weapon", id);
    }

    public ProjectileDef projectile(String id) {
        return require(projectiles.get(id), "projectile", id);
    }

    public MissileDef missile(String id) {
        return require(missiles.get(id), "missile", id);
    }

    /** The warhead with this id, or null for a null id. */
    public WarheadDef warhead(String id) {
        return id == null ? null : require(warheads.get(id), "warhead", id);
    }

    public void add(WeaponDef def) {
        weapons.put(def.id, def);
    }

    public void add(ProjectileDef def) {
        projectiles.put(def.id, def);
    }

    public void add(MissileDef def) {
        missiles.put(def.id, def);
    }

    public void add(WarheadDef def) {
        warheads.put(def.id, def);
    }

    private static <T> T require(T def, String kind, String id) {
        if (def == null) throw new IllegalArgumentException("Unknown " + kind + " def: " + id);
        return def;
    }

    /** Def type names in data files: {@code [weapon.id]}, {@code [projectile.id]}, ... */
    public static final String WEAPON = "weapon", PROJECTILE = "projectile", MISSILE = "missile", WARHEAD = "warhead";

    /** Binds the combat def classes to their data-file types. Safe to call before or after loading. */
    public static void registerTypes(DefRegistry registry) {
        registry.registerType(WEAPON, WeaponDef.class);
        registry.registerType(PROJECTILE, ProjectileDef.class);
        registry.registerType(MISSILE, MissileDef.class);
        registry.registerType(WARHEAD, WarheadDef.class);
    }

    /** Builds the combat tables from loaded data (modding-data's registry, mods merged in). */
    public static CombatDefs fromRegistry(DefRegistry registry) {
        registerTypes(registry);
        CombatDefs defs = new CombatDefs();
        for (WeaponDef d : registry.all(WEAPON, WeaponDef.class)) defs.add(d);
        for (ProjectileDef d : registry.all(PROJECTILE, ProjectileDef.class)) defs.add(d);
        for (MissileDef d : registry.all(MISSILE, MissileDef.class)) defs.add(d);
        for (WarheadDef d : registry.all(WARHEAD, WarheadDef.class)) defs.add(d);
        return defs;
    }

    /** The example defs from combat-sys/Design.md section 6, plus a light autocannon. */
    public static CombatDefs createDefault() {
        CombatDefs defs = new CombatDefs();

        ProjectileDef slug = new ProjectileDef();
        slug.id = "slug_tungsten_20kg";
        slug.mass = 20f;
        slug.muzzleSpeed = 6000f;
        slug.damageType = DamageType.KINETIC;
        slug.penetrationFactor = 1f;
        slug.ricochetAngleDeg = 72f;
        slug.ricochetRestitution = 0.6f;
        slug.spall = new SpallDef(6, 25f, 0.3f);
        slug.maxLifetime = 30f;
        defs.add(slug);

        ProjectileDef ac = new ProjectileDef();
        ac.id = "autocannon_30mm";
        ac.mass = 0.4f;
        ac.muzzleSpeed = 1500f;
        ac.damageType = DamageType.KINETIC;
        ac.penetrationFactor = 0.8f;
        ac.ricochetAngleDeg = 65f;
        ac.ricochetRestitution = 0.4f;
        ac.spall = new SpallDef(2, 20f, 0.2f);
        ac.maxLifetime = 15f;
        defs.add(ac);

        ProjectileDef he = new ProjectileDef();
        he.id = "shell_he_120mm";
        he.mass = 20f;
        he.muzzleSpeed = 800f;
        he.damageType = DamageType.EXPLOSIVE;
        // Semi-armor-piercing: holes light plating, then goes off a tile inside.
        he.penetrationFactor = 0.5f;
        he.fuse = Fuse.delayed(1);
        he.warhead = "he_shell";
        he.maxLifetime = 40f;
        defs.add(he);

        WarheadDef heShell = new WarheadDef();
        heShell.id = "he_shell";
        heShell.fragmentCount = 16;
        heShell.fragmentMass = 0.05f;
        heShell.fragmentSpeed = 1500f;
        heShell.fragmentConeDeg = 90f;
        heShell.blastRadiusTiles = 3;
        heShell.blastDamage = 6e6f;
        defs.add(heShell);

        WarheadDef frag = new WarheadDef();
        frag.id = "frag_heavy";
        frag.fragmentCount = 40;
        frag.fragmentMass = 0.2f;
        frag.fragmentSpeed = 2500f;
        frag.fragmentConeDeg = 60f;
        frag.blastRadiusTiles = 3;
        frag.blastDamage = 4e5f;
        defs.add(frag);

        MissileDef harpoon = new MissileDef();
        harpoon.id = "harpoon";
        harpoon.dryMass = 150f;
        harpoon.propellantMass = 250f;
        harpoon.exhaustVelocity = 3000f;
        harpoon.maxThrust = 60000f;
        harpoon.turnRateDeg = 90f;
        harpoon.seeker.type = MissileDef.SeekerType.RADAR;
        harpoon.seeker.fovDeg = 30f;
        harpoon.seeker.range = 20000f;
        harpoon.seeker.discrimination = 0.6f;
        harpoon.navGain = 4f;
        harpoon.terminalReserve = 0.3f;
        harpoon.armingDelay = 0.6f;
        harpoon.fuse = Fuse.proximity(15f);
        harpoon.warhead = "frag_heavy";
        defs.add(harpoon);

        WeaponDef railgun = new WeaponDef();
        railgun.id = "railgun_mk1";
        railgun.mount = WeaponDef.MountType.TURRET;
        railgun.slewRateDeg = 40f;
        railgun.firingArcDeg = 270f;
        railgun.cooldown = 1.5f;
        railgun.powerDraw = 2.0e6f;
        railgun.projectile = slug.id;
        railgun.spreadRad = 0.0005f;
        defs.add(railgun);

        WeaponDef autocannon = new WeaponDef();
        autocannon.id = "autocannon_mk1";
        autocannon.mount = WeaponDef.MountType.TURRET;
        autocannon.slewRateDeg = 120f;
        autocannon.firingArcDeg = 300f;
        autocannon.cooldown = 0.1f;
        autocannon.powerDraw = 5e4f;
        autocannon.projectile = ac.id;
        autocannon.spreadRad = 0.003f;
        defs.add(autocannon);

        WeaponDef launcher = new WeaponDef();
        launcher.id = "harpoon_launcher";
        launcher.mount = WeaponDef.MountType.LAUNCHER;
        launcher.firingArcDeg = 360f;
        launcher.cooldown = 8f;
        launcher.missile = harpoon.id;
        defs.add(launcher);

        return defs;
    }
}
