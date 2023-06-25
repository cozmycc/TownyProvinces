package io.github.townyadvanced.townyprovinces.jobs.land_validation;

import com.palmergames.bukkit.towny.object.Translatable;
import io.github.townyadvanced.townyprovinces.TownyProvinces;
import io.github.townyadvanced.townyprovinces.data.DataHandlerUtil;
import io.github.townyadvanced.townyprovinces.data.TownyProvincesDataHolder;
import io.github.townyadvanced.townyprovinces.messaging.Messaging;
import io.github.townyadvanced.townyprovinces.objects.Province;
import io.github.townyadvanced.townyprovinces.objects.TPCoord;
import io.github.townyadvanced.townyprovinces.settings.TownyProvincesSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LandvalidationTask extends BukkitRunnable {
	
	@Override
	public void run() {
		TownyProvinces.info("Acquiring land validation lock.");
		synchronized (TownyProvinces.LAND_VALIDATION_LOCK) {
			TownyProvinces.info("Land validation lock acquired.");
			TownyProvinces.info("Land Validation Job Starting.");
			/*
			 * If there are no requests pending, 
			 * this is a fresh start, so request all provinces
			 */
			if(!areAnyValidationsPending()) {
				setLandValidationRequestsForAllProvinces(true);
			}
			executeLandValidation();
		}
	}

	private boolean areAnyValidationsPending() {
		for(Province province: TownyProvincesDataHolder.getInstance().getProvincesSet()) {
			if(province.isLandValidationRequested()) {
				return true;
			}
		}
		return false;
	}
	
	private void setLandValidationRequestsForAllProvinces(boolean value) {
		for(Province province: TownyProvincesDataHolder.getInstance().getProvincesSet()) {
			if(province.isLandValidationRequested() != value) {
				province.setLandValidationRequested(value);
				province.saveData();
			}
		}
	}
	
	/**
	 * Go through each province,
	 * And decide if it is land or sea,
	 * then set the isSea boolean as appropriate
	 * <p>
	 * This method will not always work perfectly
	 * because it checks only a selection if the biomes.
	 * It does this because checking a biome is hard on the processor
	 * <p>
	 * Mistakes are expected,
	 * which is why server owners can run /tp province sea x,y
	 */
	private void executeLandValidation() {
		TownyProvinces.info("Now Running land validation job.");
		double numProvincesProcessed = 0;
		Set<Province> copyOfProvincesSet = new HashSet<>(TownyProvincesDataHolder.getInstance().getProvincesSet());
		for(Province province : copyOfProvincesSet) {
			if(!province.isLandValidationRequested())
				numProvincesProcessed++;  //Already processed
		}
		for(Province province: copyOfProvincesSet) {
			if (province.isLandValidationRequested()) {
				boolean isSea = isProvinceMainlyOcean(province);
				if(isSea != province.isSea()) {
					province.setSea(isSea);
				}
				province.setLandValidationRequested(false);
				numProvincesProcessed++;
			}
			int percentCompletion = (int)((numProvincesProcessed / copyOfProvincesSet.size()) * 100);
			TownyProvinces.info("Land Validation Job Progress: " + percentCompletion + "%");

			//Handle any stop requests
			LandValidationJobStatus landValidationJobStatus = LandValidationTaskController.getLandValidationJobStatus();
			switch (landValidationJobStatus) {
				case STOP_REQUESTED:
					TownyProvinces.info("Land Validation Task: Clearing all validation requests");
					setLandValidationRequestsForAllProvinces(false);  //Clear all requests
					TownyProvinces.info("Land Validation Task: Saving data");
					DataHandlerUtil.saveAllData();
					LandValidationTaskController.stopTask();
					return;
				case PAUSE_REQUESTED:
					TownyProvinces.info("Land Validation Task: Saving data");
					DataHandlerUtil.saveAllData();
					LandValidationTaskController.pauseTask();
					return;
				case RESTART_REQUESTED:
					TownyProvinces.info("Land Validation Task: Clearing all validation requests");
					setLandValidationRequestsForAllProvinces(false);  //Clear all requests
					TownyProvinces.info("Land Validation Task: Saving data");
					DataHandlerUtil.saveAllData();
					LandValidationTaskController.restartTask();
					return;
			}
		}
		TownyProvinces.info("Land Validation Task: Saving data");
		DataHandlerUtil.saveAllData();
		LandValidationTaskController.stopTask();
		TownyProvinces.info("Land Validation Job Complete.");
	}

	private static boolean isProvinceMainlyOcean(Province province) {
		List<TPCoord> coordsInProvince = province.getListOfCoordsInProvince();
		String worldName = TownyProvincesSettings.getWorldName();
		World world = Bukkit.getWorld(worldName);
		Biome biome;
		TPCoord coordToTest;
		for(int i = 0; i < 10; i++) {
			coordToTest = coordsInProvince.get((int)(Math.random() * coordsInProvince.size()));
			int x = (coordToTest.getX() * TownyProvincesSettings.getChunkSideLength()) + 8;
			int z = (coordToTest.getZ() * TownyProvincesSettings.getChunkSideLength()) + 8;
			biome = world.getHighestBlockAt(x,z).getBiome();
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if(!biome.name().toLowerCase().contains("ocean") && !biome.name().toLowerCase().contains("beach")) {
				return false;
			}
		}
		return true;
	}

}
