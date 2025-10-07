#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mount.h>
#include <sys/sysmacros.h>
#include <sys/xattr.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <libfiemap/image_manager.h>
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/file.h>
#include <android-base/strings.h>
#include <fs_mgr.h>
#include <linux/kdev_t.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <fcntl.h>
#include <errno.h>
#include <string>
#include <string.h>
#include <unistd.h>
#include <android-base/logging.h>


using namespace std::chrono_literals;
using namespace std::string_literals;
using android::fiemap::IImageManager;


std::string getNextSlot() {
    std::string current_slot;
    std::string next_slot = "a";
    if(android::base::ReadFileToString("/metadata/phh/img", &current_slot) &&
        current_slot.c_str()[0] == 'a') {
            next_slot = "b";
    }
    return next_slot;
}

/*int64_t getImageSize() {
	return std::stoull(android::base::GetProperty("phh.ota.size", "3072"));
}*/

void writeProgress(uint64_t current, uint64_t total) {
	android::base::SetProperty("phh.ota.progress", std::to_string(100 * current / total));
}

void writeImageError(std::string error) {
	android::base::SetProperty("phh.ota.error", std::move(error));
}

static void log_kmsg(const std::string& s) {
	android::base::WriteStringToFile("phh_check: " + s, "/dev/kmsg");
}

bool checkOta(std::string image) {
	// Make sure dm & userdata devices are initialized (mirrors init)
/*	BlockDevInitializer block_dev_init;
	block_dev_init.InitDeviceMapper();
	block_dev_init.InitDevices({"userdata"});*/
	auto images = IImageManager::Open("phh", 0ms);
	if (!images) {
		log_kmsg("IImageManager::Open failed");
		return false;
	}

	// Map the dm device (this creates /dev/block/dm-*)
	std::string devPath;
	if (!images->MapImageDevice(image, std::chrono::seconds(10), &devPath)) {
		log_kmsg("MapImageDevice failed for " + image);
		if (!images->GetMappedImageDevice(image, &devPath)) {
			log_kmsg("GetMappedImageDevice failed for " + image);
			images->UnmapImageDevice(image);
			return false;
		}
	}

	log_kmsg("device path is: " + devPath);
	if (devPath.empty()) {
		return false;
	}

	// Ensure mountpoint exists
	const char* mountPoint = "/mnt/phh_ota_check";
	mkdir("/mnt", 0755);
	mkdir(mountPoint, 0755);

	// Try read-only mount
	log_kmsg("mounting");
	int mountRes = mount(devPath.c_str(), mountPoint, "ext4", MS_RDONLY, "");
	log_kmsg(
			"test mount " + devPath + " -> " + mountPoint +
			" returned " + std::to_string(mountRes) + " errno=" + std::to_string(errno));

	bool mounted = (mountRes == 0);

	log_kmsg("cleaning");
	// Cleanup
	if (mounted) {
		if (umount2(mountPoint, 0) != 0) umount2(mountPoint, MNT_DETACH);
	}
	if (!images->UnmapImageDevice(image)) {
		log_kmsg("UnmapImageDevice failed for " + image);
	}

	return mounted;
}

int main(int argc, char **argv) {
	android::base::InitLogging(argv, android::base::KernelLogger);
	android::base::SetDefaultTag("phh_check");
	android::base::SetMinimumLogSeverity(android::base::VERBOSE);
	mkdir("/metadata/gsi/phh", 0771);
	chown("/metadata/gsi/phh", 0, 1000);
	mkdir("/data/gsi/phh", 0771);
	chown("/data/gsi/phh", 0, 1000);

	auto imgManager = IImageManager::Open("phh", 0ms);
	if(argc>=2 && strcmp(argv[1], "unmap") == 0) {
		fprintf(stderr, "Unmapping backing image returned %s\n", imgManager->UnmapImageDevice("system_otaphh_a") ? "true" : "false");
		fprintf(stderr, "Unmapping backing image returned %s\n", imgManager->UnmapImageDevice("system_otaphh_b") ? "true" : "false");
		return 0;
	}
	if(argc>=2 && strcmp(argv[1], "switch-slot") == 0) {
		writeImageError("");
		std::string next_slot = getNextSlot();
		std::string imageName = "system_otaphh_"s + next_slot;
		if (!checkOta(imageName)) {
			writeImageError("Failed to mount OTA image.");
			return -1;
		}
		mkdir("/metadata/phh", 0700);
		android::base::WriteStringToFile(next_slot, "/metadata/phh/img");
		return 0;
	}
	if(argc>=2 && strcmp(argv[1], "new-slot") == 0) {
		writeImageError("");
		std::string next_slot = getNextSlot();

		std::string imageName = "system_otaphh_"s + next_slot;

		fprintf(stderr, "Unmapping backing image returned %s\n", imgManager->UnmapImageDevice(imageName) ? "true" : "false");
		fprintf(stderr, "Deleting backing image returned %s\n", imgManager->DeleteBackingImage(imageName) ? "true" : "false");
		
		auto backRes = imgManager->CreateBackingImage(imageName, 2300*1024*1024LL, IImageManager::CREATE_IMAGE_DEFAULT, [](uint64_t c, uint64_t t){ writeProgress(c, t); return true; });
		if(backRes.is_ok()) {
			fprintf(stderr, "Creating system image succeeded\n");
		} else {
			writeImageError(backRes.string());
			fprintf(stderr, "Creating system image failed\n");
			return -1;
		}

		std::string blockDev;
		fprintf(stderr, "Mapping backing image returned %s\n", imgManager->MapImageDevice(imageName, 0ms, &blockDev) ? "true" : "false");
		fprintf(stderr, "blockdev is %s\n", blockDev.c_str());
		printf("%s\n", blockDev.c_str());

		struct stat sb;
		for(int i=0; i<10; i++) {
			if(!stat(blockDev.c_str(), &sb)) break;
			sleep(1);
		}

		if(!S_ISBLK(sb.st_mode)) {
			fprintf(stderr, "blockDev wasn't block dev\n");
			return -1;
		}

		unlink("/dev/phh-ota");
		mknod("/dev/phh-ota", 0664 | S_IFBLK, makedev(major(sb.st_rdev), minor(sb.st_rdev)));
		chmod("/dev/phh-ota", 0664);
		// Allow system uid to write there
		chown("/dev/phh-ota", 0, 1000);
		const char *dstContext = "u:object_r:phhota_dev:s0";
		setxattr("/dev/phh-ota", "security.selinux", dstContext, strlen(dstContext), 0);

		return 0;
	}
	if(argc>=2 && strcmp(argv[1], "delete-other-slot") == 0) {
		const char* current_slot = getenv("PHH_OTA_SLOT");
		if(current_slot == NULL) {
			imgManager->UnmapImageDevice("system_otaphh_a");
			imgManager->DeleteBackingImage("system_otaphh_a");
			imgManager->UnmapImageDevice("system_otaphh_b");
			imgManager->DeleteBackingImage("system_otaphh_b");
			return 0;
		}
		if(current_slot[0] == 'a') {
			imgManager->UnmapImageDevice("system_otaphh_b");
			imgManager->DeleteBackingImage("system_otaphh_b");
			return 0;
		}
		if(current_slot[0] == 'b') {
			imgManager->UnmapImageDevice("system_otaphh_a");
			imgManager->DeleteBackingImage("system_otaphh_a");
			return 0;
		}
		return 0;
	}

	return 1;
}
