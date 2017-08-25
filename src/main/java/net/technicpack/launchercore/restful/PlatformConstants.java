/*
 * This file is part of Technic Launcher Core.
 * Copyright (C) 2013 Syndicate, LLC
 *
 * Technic Launcher Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Technic Launcher Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * as well as a copy of the GNU Lesser General Public License,
 * along with Technic Launcher Core.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.technicpack.launchercore.restful;

//TODO: Set up the API urls.
public class PlatformConstants {
	public static final String PLATFORM = "https://www.technicpack.net/";

	public static final String API = PLATFORM + "api/";

	public static final String MODPACK = API + "modpack/";

	public static final String MOD_ROOT = "https://gamerarg.com.ar/";

	public static final String NEWS = MOD_ROOT + "MCLauncher/news.php";

    public static final String VERSION = MOD_ROOT + "MCLauncher/version.php";

    public static final String DOWNLOAD = MOD_ROOT + "MCLauncher/downloads/";

    public static final String DONATION_URL = MOD_ROOT + "donar";

	public static String getPlatformInfoUrl(String modpack) {
		return MODPACK + modpack;
	}

	public static String getRunCountUrl(String modpack) {
		return getPlatformInfoUrl(modpack) + "/run";
	}

	public static String getDownloadCountUrl(String modpack) {
		return getPlatformInfoUrl(modpack) + "/download";
	}
}
