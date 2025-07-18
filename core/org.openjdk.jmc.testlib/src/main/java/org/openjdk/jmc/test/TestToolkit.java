/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at https://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.openjdk.jmc.common.io.IOToolkit;
import org.openjdk.jmc.test.io.IOResource;
import org.openjdk.jmc.test.io.IOResourceSet;
import org.openjdk.jmc.test.io.ResourceResource;

/**
 * Toolkit containing utility function for finding test files and comparing them with each other. I
 * could use the FileLocator class, but I don't want to add a dependency on Eclipse.
 */
@SuppressWarnings("nls")
public final class TestToolkit {
	private TestToolkit() {
		// Don't instantiate
	}

	/**
	 * @param clazz
	 *            use the class loader of this class to find and load resources
	 * @param directory
	 *            resource directory
	 * @param indexFile
	 *            Resource file (relative to the directory) containing names of the resources (also
	 *            relative to the directory) to be loaded. One resource name per line, leading and
	 *            trailing whitespace will be trimmed. Empty lines and lines starting with '#' will
	 *            be ignored.
	 * @return a resource set
	 */
	public static IOResourceSet getResourcesInDirectory(Class<?> clazz, String directory, String indexFile)
			throws IOException {
		return getResourcesInDirectoryWithExclusions(clazz, directory, indexFile).included;
	}

	/**
	 * Information about resources in an index file, including both included and excluded files.
	 */
	public static class IndexedResources {
		public final IOResourceSet included;
		public final Set<String> excluded;

		public IndexedResources(IOResourceSet included, Set<String> excluded) {
			this.included = included;
			this.excluded = excluded;
		}
	}

	/**
	 * Load resources listed in an index file, with exclusions from a separate exclude file. Empty
	 * lines and lines starting with '#' will be ignored in both files.
	 *
	 * @param clazz
	 *            use the class loader of this class to find and load resources
	 * @param directory
	 *            resource directory
	 * @param indexFile
	 *            Resource file containing names of all available resources
	 * @param excludeFile
	 *            Resource file containing names of resources to exclude (can be null)
	 * @return indexed resources containing included files and excluded file names
	 */
	public static IndexedResources getResourcesInDirectoryWithExclusions(
		Class<?> clazz, String directory, String indexFile, String excludeFile) throws IOException {
		// Read all available resources from index file
		Set<String> allResources = readResourceNames(clazz, directory, indexFile);

		// Read excluded resources from exclude file (if it exists)
		Set<String> excludedResources = new HashSet<>();
		if (excludeFile != null) {
			excludedResources = readResourceNames(clazz, directory, excludeFile);
		}

		// Create included resources (all - excluded)
		List<IOResource> includedResources = new ArrayList<>();
		for (String resourceName : allResources) {
			if (!excludedResources.contains(resourceName)) {
				includedResources.add(new ResourceResource(clazz, directory, resourceName));
			}
		}

		return new IndexedResources(new IOResourceSet(includedResources), excludedResources);
	}

	/**
	 * Load resources listed in an index file, with exclusions from a separate exclude file. Uses
	 * "exclude.txt" as the default exclude file name.
	 *
	 * @param clazz
	 *            use the class loader of this class to find and load resources
	 * @param directory
	 *            resource directory
	 * @param indexFile
	 *            Resource file containing names of all available resources
	 * @return indexed resources containing included files and excluded file names
	 */
	public static IndexedResources getResourcesInDirectoryWithExclusions(
		Class<?> clazz, String directory, String indexFile) throws IOException {
		return getResourcesInDirectoryWithExclusions(clazz, directory, indexFile, "exclude.txt");
	}

	/**
	 * Read resource names from a file, ignoring empty lines and comments.
	 */
	private static Set<String> readResourceNames(Class<?> clazz, String directory, String fileName) throws IOException {
		Set<String> resourceNames = new HashSet<>();
		InputStream in = null;
		BufferedReader br = null;
		try {
			in = clazz.getClassLoader().getResourceAsStream(directory + '/' + fileName);
			if (in == null) {
				// File doesn't exist, return empty set
				return resourceNames;
			}
			br = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#")) {
					resourceNames.add(line);
				}
			}
		} finally {
			IOToolkit.closeSilently(in);
			IOToolkit.closeSilently(br);
		}
		return resourceNames;
	}

	public static IOResource getNamedResource(Class<?> clazz, String directory, String fileName) throws IOException {
		String resourceName = directory + '/' + fileName;
		if (clazz.getClassLoader().getResource(resourceName) == null) {
			throw new IOException("Resource not found: " + resourceName);
		}
		return new ResourceResource(clazz, directory, fileName);
	}

	/**
	 * Asserts that two resource have the same textual content. The resource are compared line by
	 * line and if the differ an Assert.fail is triggered.
	 *
	 * @param expected
	 *            Expected result
	 * @param actual
	 *            Actual result
	 * @throws IOException
	 *             if the files could not be opened or located.
	 */
	public static void assertEquals(IOResource expected, IOResource actual) throws IOException {
		BufferedReader readerExp = null;
		BufferedReader readerAct = null;
		try {
			readerExp = new BufferedReader(new InputStreamReader(expected.open(), StandardCharsets.UTF_8));
			readerAct = new BufferedReader(new InputStreamReader(actual.open(), StandardCharsets.UTF_8));
			int lineNumber = 0;
			String expLine = null;
			while ((expLine = readerExp.readLine()) != null) {
				String actLine = readerAct.readLine();
				if (actLine == null) {
					Assert.fail("Premature end of file " + actual);
				}
				Assert.assertEquals("Actual result file " + actual + " differs from expected file " + expected
						+ " at line " + (lineNumber + 1), expLine, actLine);
				lineNumber++;
			}
			if (readerAct.readLine() != null) {
				Assert.fail("Premature end of file " + expected);
			}
		}

		finally {
			IOToolkit.closeSilently(readerExp);
			IOToolkit.closeSilently(readerAct);
		}
	}

	/**
	 * Note: Only use this method in stand-alone programs, not in test cases.
	 */
	public static File getProjectDirectory(Class<?> clazz, String directoryName) throws IOException {
		URL url = getLocation(clazz);

		File file = findParentDirectory(createFile(url), "org.openjdk.jmc");
		if (file != null) {
			return new File(file, directoryName);
		}
		throw new IOException("Could not find project directory " + url);
	}

	private static URL getLocation(Class<?> clazz) {
		return clazz.getProtectionDomain().getCodeSource().getLocation();
	}

	private static File createFile(URL url) throws IOException {
		try {
			return new File(url.toURI());
		} catch (URISyntaxException e) {
			throw new IOException("Invalid filename " + url);
		}
	}

	private static File findParentDirectory(File startDirectory, String directory) {
		for (File file = startDirectory; file != null; file = file.getParentFile()) {
			if (file.getName().startsWith(directory)) {
				return file;
			}
		}
		return null;
	}

	/**
	 * @param clazz
	 *            use the class loader of this class to find resources
	 * @param directoryName
	 *            directory to materialize
	 * @param fileName
	 *            name of resource to materialize
	 * @param directory
	 *            Directory to materialize into. This directory will be deleted (if it exists) and
	 *            recreated.
	 * @throws IOException
	 */
	private static void materialize(Class<?> clazz, String directoryName, String fileName, File directory)
			throws IOException {
		if (fileName == null) {
			throw new IOException("Must specify file name to materialize");
		}
		if (!directory.delete()) {
			throw new IOException("Could not delete directory: " + directory.getAbsolutePath());
		}
		if (!directory.mkdirs()) {
			throw new IOException("Could not create directory: " + directory.getAbsolutePath());
		}

		IOResource rr = new ResourceResource(clazz, directoryName, fileName);
		InputStream in = null;
		try {
			in = rr.open();
			if (in != null) {
				FileOutputStream os = null;
				try {
					File file = new File(directory, fileName);
					os = new FileOutputStream(file);
					IOToolkit.copy(in, os);
					os.close();
				} finally {
					IOToolkit.closeSilently(os);
				}
			}
		} finally {
			IOToolkit.closeSilently(in);
		}
	}

	/**
	 * Materializes a file from bundle into a temporary directory.
	 *
	 * @param clazz
	 *            use the class loader of this class to find resources
	 * @param directoryName
	 *            directory to materialize
	 * @param fileName
	 *            name of resource to materialize
	 * @return materialized directory
	 */
	public static File materialize(Class<?> clazz, String directoryName, String fileName) throws IOException {
		File directory = File.createTempFile(directoryName, ".dir");
		materialize(clazz, directoryName, fileName, directory);
		return directory;
	}
}
