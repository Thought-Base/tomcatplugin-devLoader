package org.apache.catalina.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;

import jakarta.servlet.ServletContext;

/**
 * @author Martin Kahr
 *
 */
public final class DevLoader extends WebappLoader {
	/** Information */
	public static final String INFO = "org.apache.catalina.loader.DevLoader/1.0";

	/** クラスパスファイル */
	private static final String WEB_CLASS_PATH_FILE = ".#webclasspath";
	/** プラグインファイル */
	private static final String TOMCAT_PLUGIN_FILE = ".tomcatplugin";

	/**
	 * @see org.apache.catalina.util.LifecycleBase#startInternal()
	 */
	@Override
	protected void startInternal() throws LifecycleException {
		log("Starting DevLoader");

		super.startInternal();

		final ClassLoader cl = getClassLoader();
		if (!(cl instanceof WebappClassLoaderBase webappClassLoader)) {
			logError("Unable to install WebappClassLoader !");
			return;
		}

		final StringBuilder classpath = new StringBuilder();
		for (final String entry : readWebClassPathEntries()) {
			File f = new File(entry);
			if (f.exists()) {
				if (f.isDirectory() && !entry.endsWith("/")) {
					f = new File(entry + "/");
				}
				final URI uri = f.toURI();
				try {
					webappClassLoader.addURL(uri.toURL());
				} catch (final MalformedURLException e) {
					logError(e.getMessage());
					continue;
				}
				classpath.append(f.toString()).append(File.pathSeparator);
				log("added " + uri.toString());
			} else {
				logError(entry + " does not exist !");
			}
		}

		final String cp = (String) getServletContext().getAttribute(Globals.CLASS_PATH_ATTR);
		if (cp != null) {
			for (final String token : cp.split(File.pathSeparator)) {
				// only on windows
				if (token.charAt(0) == '/' && token.charAt(2) == ':') {
					classpath.append(token.substring(1));
				} else {
					classpath.append(token);
				}
				classpath.append(File.pathSeparator);
			}
		}

		getServletContext().setAttribute(Globals.CLASS_PATH_ATTR, classpath.toString());

		log("JSPCompiler Classpath = " + classpath);
	}

	/**
	 * ログ出力
	 *
	 * @param msg 出力メッセージ
	 */
	protected void log(final String msg) {
		System.out.println("[DevLoader] " + msg);
	}

	/**
	 * エラー出力
	 *
	 * @param msg エラーメッセージ
	 */
	protected void logError(final String msg) {
		System.err.println("[DevLoader] Error: " + msg);
	}

	/**
	 * Webクラスパス読込
	 *
	 * @return Webクラスパス行
	 */
	protected List<String> readWebClassPathEntries() {
		final File prjDir = getProjectRootDir();
		if (prjDir == null) {
			return Collections.emptyList();
		}

		log("projectdir=" + prjDir.getAbsolutePath());

		return loadWebClassPathFile(prjDir);
	}

	/**
	 * プロジェクトルート取得
	 *
	 * @return プロジェクトルート
	 */
	protected File getProjectRootDir() {
		File rootDir = getWebappDir();
		while (rootDir != null) {
			final File[] files = rootDir.listFiles(this::accept);
			if (files != null && 0 < files.length) {
				return files[0].getParentFile();
			}
			rootDir = rootDir.getParentFile();
		}
		return null;
	}

	/**
	 * Webクラスパスファイル読込
	 *
	 * @param prjDir プロジェクトディレクトリ
	 * @return 読込行リスト
	 */
	protected List<String> loadWebClassPathFile(final File prjDir) {
		final File cpFile = new File(prjDir, WEB_CLASS_PATH_FILE);
		if (!cpFile.exists()) {
			return Collections.emptyList();
		}

		final List<String> rc = new ArrayList<>();
		try (FileInputStream fis = new FileInputStream(cpFile)) {
			try (InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
				try (BufferedReader lr = new BufferedReader(reader)) {
					String line = null;
					while ((line = lr.readLine()) != null) {
						line = line.replace('\\', '/');
						rc.add(line);
					}
				}
			}
		} catch (final IOException ex) {
			logError(ex.getMessage());
		}
		return rc;
	}

	/**
	 * サーブレットコンテキスト取得
	 *
	 * @return サーブレットコンテキスト
	 */
	protected ServletContext getServletContext() {
		return getContext().getServletContext();
	}

	/**
	 * Webアプリディレクトリ取得
	 *
	 * @return Webアプリディレクトリ
	 */
	protected File getWebappDir() {
		return new File(getServletContext().getRealPath("/"));
	}

	/**
	 * クラスパスファイル または プラグインファイル 判断
	 *
	 * @param file File
	 * @return クラスパスファイル または プラグインファイルの場合、true を返す。
	 */
	public boolean accept(final File file) {
		final String fileName = file.getName();
		return WEB_CLASS_PATH_FILE.equalsIgnoreCase(fileName)
			|| TOMCAT_PLUGIN_FILE.equalsIgnoreCase(fileName);
	}
}
