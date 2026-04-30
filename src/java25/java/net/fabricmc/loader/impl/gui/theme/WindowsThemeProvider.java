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

package net.fabricmc.loader.impl.gui.theme;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

class WindowsThemeProvider {
	private static final int HKEY_CURRENT_USER = 0x80000001;
	private static final int RRF_RT_REG_DWORD = 0x00000010;
	private static final int ERROR_SUCCESS = 0;
	private static final String KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
	private static final String VALUE = "AppsUseLightTheme";

	private final MethodHandle RegGetValueW;

	WindowsThemeProvider() {
		SymbolLookup advapi32 = SymbolLookup.libraryLookup("Advapi32", Arena.global());
		Linker linker = Linker.nativeLinker();

		// LSTATUS RegGetValueW(HKEY hkey, LPCWSTR lpSubKey, LPCWSTR lpValue, DWORD dwFlags, LPDWORD pdwType, PVOID pvData, LPDWORD pcbData)
		this.RegGetValueW = linker.downcallHandle(
				advapi32.find("RegGetValueW").orElseThrow(),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS)
		);
	}

	public String getTheme() {
		int result;
		int appsUseLightTheme;

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment subKey = arena.allocateFrom(KEY + "\0", StandardCharsets.UTF_16LE);
			MemorySegment valueName = arena.allocateFrom(VALUE + "\0", StandardCharsets.UTF_16LE);
			MemorySegment data = arena.allocate(ValueLayout.JAVA_INT);
			MemorySegment dataSize = arena.allocate(ValueLayout.JAVA_INT);
			dataSize.set(ValueLayout.JAVA_INT, 0, 4);

			result = (int) RegGetValueW.invoke(
					MemorySegment.ofAddress(HKEY_CURRENT_USER),
					subKey,
					valueName,
					RRF_RT_REG_DWORD,
					MemorySegment.NULL,
					data,
					dataSize
			);
			appsUseLightTheme = data.get(ValueLayout.JAVA_INT, 0);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to read Windows theme setting", e);
		}

		if (result != ERROR_SUCCESS) {
			throw new RuntimeException("RegGetValueW failed with error code: " + result);
		}

		return appsUseLightTheme == 1 ? "light" : "dark";
	}
}
