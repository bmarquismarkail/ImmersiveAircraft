package immersive_aircraft.forge.cobalt.registration;

import immersive_aircraft.cobalt.registration.Registration;
import immersive_aircraft.forge.ForgeBusEvents;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.*;
import java.util.function.Supplier;

/**
 * Contains all the crap required to interface with forge's code
 */
public class RegistrationImpl extends Registration.Impl {
    @SuppressWarnings("unused")
    public static final RegistrationImpl IMPL = new RegistrationImpl();

    private final Map<String, RegistryRepo> repos = new HashMap<>();
    private final DataLoaderRegister dataLoaderRegister = new DataLoaderRegister();

    public RegistrationImpl() {
        ForgeBusEvents.DATA_REGISTRY = dataLoaderRegister;
    }

    public static void bootstrap() {
        //nop
    }

    private RegistryRepo getRepo(String namespace) {
        return repos.computeIfAbsent(namespace, RegistryRepo::new);
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(EntityType<T> type, EntityRendererProvider<T> constructor) {
        EntityRenderers.register(type, constructor);
    }

    @Override
    public void registerDataLoader(ResourceLocation id, SimpleJsonResourceReloadListener loader) {
        dataLoaderRegister.dataLoaders.add(loader);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> Supplier<T> register(Registry<? super T> registry, ResourceLocation id, Supplier<T> obj) {
        DeferredRegister reg = getRepo(id.getNamespace()).get(registry);
        return reg.register(id.getPath(), obj);
    }

    @Override
    public CreativeModeTab itemGroup(ResourceLocation id, Supplier<ItemStack> icon) {
        return new CreativeModeTab(CreativeModeTab.getGroupCountSafe(), String.format("%s.%s", id.getNamespace(), id.getPath())) {
            @Override
            public ItemStack makeIcon() {
                return icon.get();
            }
        };
    }

    static class RegistryRepo {
        private final Set<ResourceLocation> skipped = new HashSet<>();
        private final Map<ResourceLocation, DeferredRegister<?>> registries = new HashMap<>();

        private final String namespace;

        public RegistryRepo(String namespace) {
            this.namespace = namespace;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> DeferredRegister get(Registry<? super T> registry) {
            ResourceLocation id = registry.key().location();
            if (!registries.containsKey(id) && !skipped.contains(id)) {
                ForgeRegistry reg = RegistryManager.ACTIVE.getRegistry(id);
                if (reg == null) {
                    skipped.add(id);
                    return null;
                }

                DeferredRegister def = DeferredRegister.create(Objects.requireNonNull(reg, "Registry=" + id), namespace);

                def.register(FMLJavaModLoadingContext.get().getModEventBus());

                registries.put(id, def);
            }

            return registries.get(id);
        }
    }

    public static class DataLoaderRegister {

        private final List<SimpleJsonResourceReloadListener> dataLoaders = new ArrayList<>(); // Doing no setter means only the RegistrationImpl class can get access to registering more loaders.

        public List<SimpleJsonResourceReloadListener> getLoaders() {
            return dataLoaders;
        }

    }

}
