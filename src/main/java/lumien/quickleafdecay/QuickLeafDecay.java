package lumien.quickleafdecay;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lumien.quickleafdecay.config.QuickLeafDecayConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(value = QuickLeafDecay.MOD_ID)
public class QuickLeafDecay
{
	final public static String MOD_ID = "quickleafdecay";
	final static String MOD_NAME = "Quick Leaf Decay";
	final static String MOD_VERSION = "@VERSION@";

	static Random rng = new Random();

	public static QuickLeafDecay INSTANCE;

	public QuickLeafDecayConfig config;

//	public net.minecraft.util.Direction getNormal()
//	{
//		return this.normal; // Not quite sure what this does for me, but might fix a problem with private access
//	}

	public QuickLeafDecay()
	{
		INSTANCE = this;

		final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::preInit);

		MinecraftForge.EVENT_BUS.addListener(this::breakBlock);
		MinecraftForge.EVENT_BUS.addListener(this::notifyNeighbors);
		MinecraftForge.EVENT_BUS.addListener(LeafTickScheduler.INSTANCE::tick);

		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QuickLeafDecayConfig.spec);
		modEventBus.register(QuickLeafDecayConfig.class);
	}

	public void preInit(FMLCommonSetupEvent event)
	{

	}

	Cache<BlockPos, Integer> brokenBlockCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).maximumSize(200).build();

	public void breakBlock(BreakEvent event)
	{
		if (QuickLeafDecayConfig.playerDecay.get() && !(event.getPlayer() instanceof FakePlayer) && !event.getWorld().isClientSide()) // isClientSide might not be opposite of isRemote? Let's try it anyway
		{
			brokenBlockCache.put(event.getPos(), 0);
		}
	}

	public void notifyNeighbors(NeighborNotifyEvent event)
	{
		if (!event.getWorld().isClientSide() && !QuickLeafDecayConfig.playerDecay.get() || brokenBlockCache.getIfPresent(event.getPos()) != null)
		{
			ServerWorld world = (ServerWorld) event.getWorld();

			BlockState notifierState = event.getState();
			Block b = notifierState.getBlock();

			if (b.isAir(notifierState, world, event.getPos()))
			{
				if (QuickLeafDecayConfig.playerDecay.get())
					brokenBlockCache.invalidate(event.getPos());

				for (Direction direction : event.getNotifiedSides())
				{
					BlockPos offPos = event.getPos().offset(direction.getNormal()); // Normal could break all of this and send it to hell, but might as well try

					if (world.isLoaded(offPos))
					{
						BlockState state = world.getBlockState(offPos);

						if (BlockTags.LEAVES.contains(state.getBlock()))
						{
							if (QuickLeafDecayConfig.playerDecay.get())
								brokenBlockCache.put(offPos, 0);

							LeafTickScheduler.INSTANCE.schedule(world, offPos, QuickLeafDecayConfig.decaySpeed.get() + (QuickLeafDecayConfig.decayFuzz.get() > 0 ? rng.nextInt(QuickLeafDecayConfig.decayFuzz.get()) : 0));
						}
					}
				}
			}
		}
	}
}
