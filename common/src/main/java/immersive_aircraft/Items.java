package immersive_aircraft;

import immersive_aircraft.cobalt.registration.Registration;
import immersive_aircraft.entity.*;
import immersive_aircraft.item.AircraftItem;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public interface Items {
    Supplier<Item> HULL = register("hull", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> ENGINE = register("engine", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> SAIL = register("sail", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> PROPELLER = register("propeller", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> BOILER = register("boiler", () -> new Item(baseProps().stacksTo(8)));

    Supplier<Item> AIRSHIP = register("airship", () -> new AircraftItem(baseProps().stacksTo(1), (world) -> new AirshipEntity(Entities.AIRSHIP.get(), world)));
    Supplier<Item> CARGO_AIRSHIP = register("cargo_airship", () -> new AircraftItem(baseProps().stacksTo(1), (world) -> new CargoAirshipEntity(Entities.CARGO_AIRSHIP.get(), world)));
    Supplier<Item> BIPLANE = register("biplane", () -> new AircraftItem(baseProps().stacksTo(1), (world) -> new BiplaneEntity(Entities.BIPLANE.get(), world)));
    Supplier<Item> GYRODYNE = register("gyrodyne", () -> new AircraftItem(baseProps().stacksTo(1), (world) -> new GyrodyneEntity(Entities.GYRODYNE.get(), world)));
    Supplier<Item> QUADROCOPTER = register("quadrocopter", () -> new AircraftItem(baseProps().stacksTo(1), (world) -> new QuadrocopterEntity(Entities.QUADROCOPTER.get(), world)));

    Supplier<Item> ENHANCED_PROPELLER = register("enhanced_propeller", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> ECO_ENGINE = register("eco_engine", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> NETHER_ENGINE = register("nether_engine", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> STEEL_BOILER = register("steel_boiler", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> INDUSTRIAL_GEARS = register("industrial_gears", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> STURDY_PIPES = register("sturdy_pipes", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> GYROSCOPE = register("gyroscope", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> HULL_REINFORCEMENT = register("hull_reinforcement", () -> new Item(baseProps().stacksTo(8)));
    Supplier<Item> IMPROVED_LANDING_GEAR = register("improved_landing_gear", () -> new Item(baseProps().stacksTo(8)));

    static Supplier<Item> register(String name, Supplier<Item> item) {
        return Registration.register(Registry.ITEM, Main.locate(name), item);
    }

    static void bootstrap() {
    }

    static Item.Properties baseProps() {
        return new Item.Properties().tab(ItemGroups.GROUP);
    }
}
