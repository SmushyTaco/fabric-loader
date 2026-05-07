/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiData;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricTreeWarningLevel;

public class FabricIcons {
	private static Map<String, DependencyGuiIconSource> dependencyGuiIconSources = java.util.Collections.emptyMap();
	private static final Icon missingIcon = missingIcon();
	private static final Map<String, Icon> modIconCache = new HashMap<>();
	private static final Map<String, Icon> uiIconCache = new HashMap<>();

	static Icon loadModIcon(String modId, int size) {
		if (modId == null || modId.isEmpty()) {
			return null;
		}

		String cacheKey = modId + "@" + size;
		Icon cached = modIconCache.get(cacheKey);

		if (cached != null) {
			return cached;
		}

		Icon icon = findModIcon(modId, size);

		if (icon != null) {
			modIconCache.put(cacheKey, icon);
		}

		return icon;
	}

	private static Icon findModIcon(String modId, int size) {
		Icon bundledIcon = loadBundledModIcon(modId, size);

		if (bundledIcon != null) {
			return bundledIcon;
		}

		DependencyGuiIconSource iconSource = dependencyGuiIconSources.get(modId);

		if (iconSource != null) {
			Icon icon = loadIconFromSerializedSource(iconSource, size);

			if (icon != null) {
				return icon;
			}
		}

		for (ModCandidateImpl candidate : FabricMainWindow.getDiscoveredModCandidates()) {
			if (!modId.equals(candidate.getId())) {
				continue;
			}

			Optional<String> iconPath = candidate.getMetadata().getIconPath(size);

			if (!iconPath.isPresent() || !candidate.hasPath()) {
				continue;
			}

			Icon icon = loadIconFromModPaths(candidate.getPaths(), iconPath.get(), size);

			if (icon != null) {
				return icon;
			}
		}

		return null;
	}

	static Icon getActionIcon(String targetId, int size) {
		if (targetId != null && !targetId.isEmpty()) {
			Icon icon = FabricIcons.loadModIcon(targetId, size);

			if (icon != null) {
				return icon;
			}
		}

		return UIIcon.ICON_DOCUMENT.obtain(size, FabricMainWindow.secondaryTextColor());
	}

	static void setIconSources(DependencyGuiData structuredData) {
		dependencyGuiIconSources = structuredData != null ? structuredData.iconSources : java.util.Collections.emptyMap();
	}

	private static Icon loadBundledModIcon(String modId, int size) {
		if ("minecraft".equals(modId)) {
			return loadBundledIcon("/ui/icon/minecraft_x32.png", size);
		}

		if ("java".equals(modId)) {
			return loadBundledIcon("/ui/icon/java_x32.png", size);
		}

		return null;
	}

	private static Icon loadBundledIcon(String path, int size) {
		try {
			BufferedImage image = loadImage(path);
			return new ImageIcon(scaleImage(image, size));
		} catch (IOException e) {
			return null;
		}
	}

	private static Icon loadIconFromSerializedSource(DependencyGuiIconSource iconSource, int size) {
		if (iconSource.iconBytes.length > 0) {
			try {
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(iconSource.iconBytes));

				if (image != null) {
					return new ImageIcon(scaleImage(image, size));
				}
			} catch (IOException ignored) {
				// Fall back to path based loading below.
			}
		}

		List<Path> paths = new ArrayList<>();

		for (String path : iconSource.paths) {
			if (path != null && !path.isEmpty()) {
				paths.add(java.nio.file.Paths.get(path));
			}
		}

		return loadIconFromModPaths(paths, iconSource.iconPath, size);
	}

	private static Icon loadIconFromModPaths(List<Path> paths, String iconPath, int size) {
		String normalizedIconPath = iconPath.replace('\\', '/');

		for (Path path : paths) {
			try {
				BufferedImage image;

				if (Files.isDirectory(path)) {
					Path resolvedIconPath = path;

					for (String part : normalizedIconPath.split("/")) {
						if (!part.isEmpty()) {
							resolvedIconPath = resolvedIconPath.resolve(part);
						}
					}

					if (!Files.isRegularFile(resolvedIconPath)) {
						continue;
					}

					image = ImageIO.read(resolvedIconPath.toFile());
				} else {
					try (ZipFile zip = new ZipFile(path.toFile())) {
						ZipEntry entry = zip.getEntry(normalizedIconPath);

						if (entry == null) {
							continue;
						}

						try (InputStream input = zip.getInputStream(entry)) {
							image = ImageIO.read(input);
						}
					}
				}

				if (image == null) {
					continue;
				}

				return new ImageIcon(scaleImage(image, size));
			} catch (Throwable ignored) {
				// Invalid, missing or unreadable icons should not prevent the error UI from opening.
			}
		}

		return null;
	}

	static BufferedImage loadImage(String str) throws IOException {
		return ImageIO.read(loadStream(str));
	}

	private static InputStream loadStream(String str) throws FileNotFoundException {
		InputStream stream = FabricMainWindow.class.getResourceAsStream(str);

		if (stream == null) {
			throw new FileNotFoundException(str);
		}

		return stream;
	}

	private static Image scaleImage(BufferedImage image, int size) {
		if (image.getWidth() == size && image.getHeight() == size) {
			return image;
		}

		return image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
	}

	static final class IconSet {
		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<IconInfo, Map<Integer, Icon>> icons = new HashMap<>();

		public Icon get(IconInfo info) {
			// TODO: HDPI

			int scale = 16;
			Map<Integer, Icon> map = icons.computeIfAbsent(info, k -> new HashMap<>());

			Icon icon = map.get(scale);

			if (icon == null) {
				try {
					icon = loadIcon(info, scale);
				} catch (IOException e) {
					e.printStackTrace();
					icon = FabricIcons.missingIcon();
				}

				map.put(scale, icon);
			}

			return icon;
		}
	}

	private static Icon loadIcon(IconInfo info, int scale) throws IOException {
		BufferedImage img = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D imgG2d = img.createGraphics();

		BufferedImage main = FabricIcons.loadImage("/ui/icon/" + info.mainPath + "_x" + scale + ".png");
		assert main.getWidth() == scale;
		assert main.getHeight() == scale;
		imgG2d.drawImage(main, null, 0, 0);

		final int[][] coords = { { 0, 8 }, { 8, 8 }, { 8, 0 } };

		for (int i = 0; i < info.decor.length; i++) {
			String decor = info.decor[i];

			if (decor == null) {
				continue;
			}

			BufferedImage decorImg = FabricIcons.loadImage("/ui/icon/decoration/" + decor + "_x" + (scale / 2) + ".png");
			assert decorImg.getWidth() == scale / 2;
			assert decorImg.getHeight() == scale / 2;
			imgG2d.drawImage(decorImg, null, coords[i][0], coords[i][1]);
		}

		return new ImageIcon(img);
	}

	static final class DependencyGuiIconSource {
		public final String id;
		public final String iconPath;
		public final List<String> paths = new ArrayList<>();
		public final byte[] iconBytes;

		DependencyGuiIconSource(String id, String iconPath, List<String> paths, byte[] iconBytes) {
			this.id = Objects.requireNonNull(id, "null id");
			this.iconPath = Objects.requireNonNull(iconPath, "null iconPath");

			if (paths != null) {
				this.paths.addAll(paths);
			}

			this.iconBytes = iconBytes == null ? new byte[0] : iconBytes.clone();
		}

		DependencyGuiIconSource(DataInputStream is) throws IOException {
			id = is.readUTF();
			iconPath = is.readUTF();

			for (int i = is.readInt(); i > 0; i--) {
				paths.add(is.readUTF());
			}

			int iconByteCount = is.readInt();
			iconBytes = new byte[iconByteCount];

			if (iconByteCount > 0) {
				is.readFully(iconBytes);
			}
		}

		void writeTo(DataOutputStream os) throws IOException {
			os.writeUTF(id);
			os.writeUTF(iconPath);
			os.writeInt(paths.size());

			for (String path : paths) {
				os.writeUTF(path);
			}

			os.writeInt(iconBytes.length);
			os.write(iconBytes);
		}
	}

	static final class IconInfo {
		public final String mainPath;
		public final String[] decor;
		private final int hash;

		IconInfo(String mainPath) {
			this.mainPath = mainPath;
			this.decor = new String[0];
			hash = mainPath.hashCode();
		}

		IconInfo(String mainPath, String[] decor) {
			this.mainPath = mainPath;
			this.decor = decor;
			assert decor.length < 4 : "Cannot fit more than 3 decorations into an image (and leave space for the background)";

			if (decor.length == 0) {
				// To mirror the no-decor constructor
				hash = mainPath.hashCode();
			} else {
				hash = mainPath.hashCode() * 31 + Arrays.hashCode(decor);
			}
		}

		public static IconInfo fromNode(FabricStatusNode node) {
			String[] split = node.iconType.split("\\+");

			if (split.length == 1 && split[0].isEmpty()) {
				split = new String[0];
			}

			final String main;
			List<String> decors = new ArrayList<>();
			FabricTreeWarningLevel warnLevel = node.getMaximumWarningLevel();

			if (split.length == 0) {
				// Empty string, but we might replace it with a warning
				if (warnLevel == FabricTreeWarningLevel.NONE) {
					main = "missing";
				} else {
					main = "level_" + warnLevel.lowerCaseName;
				}
			} else {
				main = split[0];

				if (warnLevel == FabricTreeWarningLevel.NONE) {
					// Just to add a gap
					decors.add(null);
				} else {
					decors.add("level_" + warnLevel.lowerCaseName);
				}

				for (int i = 1; i < split.length && i < 3; i++) {
					decors.add(split[i]);
				}
			}

			return new IconInfo(main, decors.toArray(new String[0]));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}

			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}

			IconInfo other = (IconInfo) obj;
			return mainPath.equals(other.mainPath) && Arrays.equals(decor, other.decor);
		}
	}

	enum UIIcon {
		ICON_CIRCLE("/ui/icon/circle_x24.png"),
		ICON_CLIPBOARD("/ui/icon/clipboard_x24.png"),
		ICON_DOCUMENT("/ui/icon/document_x24.png"),
		ICON_ERROR("/ui/icon/error_x24.png");

		private final String path;

		UIIcon(String path) {
			this.path = path;
		}

		public Icon obtain(int size, Color color) {
			return loadUiIcon(path, size, color);
		}

		private static Icon loadUiIcon(String path, int size, Color color) {
			String cacheKey = path + "@" + size + "@" + color.getRGB();
			Icon cached = uiIconCache.get(cacheKey);

			if (cached != null) {
				return cached;
			}

			try {
				BufferedImage image = tintImage(loadImage(path), color);
				Icon icon = new ImageIcon(scaleImage(image, size));
				uiIconCache.put(cacheKey, icon);
				return icon;
			} catch (IOException e) {
				return FabricIcons.missingIcon();
			}
		}
	}

	private static BufferedImage tintImage(BufferedImage image, Color color) {
		BufferedImage tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int colorRgb = color.getRGB() & 0x00_FF_FF_FF;

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getRGB(x, y) >>> 24;

				if (alpha != 0) {
					tinted.setRGB(x, y, (alpha << 24) | colorRgb);
				}
			}
		}

		return tinted;
	}

	static Icon missingIcon() {
		if (missingIcon == null) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);

			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					img.setRGB(x, y, 0xff_ff_f2);
				}
			}

			for (int i = 0; i < 16; i++) {
				img.setRGB(0, i, 0x22_22_22);
				img.setRGB(15, i, 0x22_22_22);
				img.setRGB(i, 0, 0x22_22_22);
				img.setRGB(i, 15, 0x22_22_22);
			}

			for (int i = 3; i < 13; i++) {
				img.setRGB(i, i, 0x9b_00_00);
				img.setRGB(i, 16 - i, 0x9b_00_00);
			}

			return new ImageIcon(img);
		}

		return missingIcon;
	}
}
