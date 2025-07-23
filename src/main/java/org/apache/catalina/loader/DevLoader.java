package org.apache.catalina.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceRoot.ResourceSetType;

import jakarta.servlet.ServletContext;

/**
 * @author Martin Kahr
 *
 */
public final class DevLoader extends WebappLoader {
	/** Information */
	public static final String INFO = "org.apache.catalina.loader.DevLoader/11.0";

	/** クラスパスファイル */
	private static final String WEB_CLASS_PATH_FILE = ".#webclasspath";
	/** プラグインファイル */
	private static final String TOMCAT_PLUGIN_FILE = ".tomcatplugin";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void startInternal() throws LifecycleException {
		log("Starting DevLoader");

		final WebResourceRoot resourceRoot = getContext().getResources();
		resourceRoot.setCachingAllowed(false);

		final List<File> entries = readWebClassPathEntries().stream().map(File::new).filter(File::exists).toList();

		for (final File f : entries) {
			if (f.isDirectory()) {
				final var url = toURL(f);
				if (url != null) {
					resourceRoot.createWebResourceSet(ResourceSetType.CLASSES_JAR, "/WEB-INF/classes", url, "/");
					log("added " + f.toURI());
				}
			}
		}

		super.startInternal();

		final ClassLoader cl = getClassLoader();
		if (!(cl instanceof WebappClassLoaderBase webappClassLoader)) {
			logError("Unable to install WebappClassLoader !");
			return;
		}

		for (final File f : entries) {
			if (!f.isDirectory()) {
				final var url = toURL(f);
				if (url != null) {
					webappClassLoader.addURL(url);
					log("added " + f.toURI());
				}
			}
		}

		final String classpath = entries.stream().map(File::toString).collect(Collectors.joining(File.pathSeparator));
		if (!classpath.isEmpty()) {
			final var cp = classpath + getClassPathAttribute();
			getServletContext().setAttribute(Globals.CLASS_PATH_ATTR, cp);
			log("JSPCompiler Classpath = " + cp);
		}
	}

	/**
	 * クラスパス属性文字列取得
	 *
	 * @return クラスパス属性文字列
	 */
	private String getClassPathAttribute() {
		final String cp = Objects.toString(getServletContext().getAttribute(Globals.CLASS_PATH_ATTR), "");
		return cp.isEmpty() ? "" : File.pathSeparator + cp;
	}

	/**
	 * URL変換
	 *
	 * @param f File
	 * @return URL
	 */
	private URL toURL(final File f) {
		try {
			return f.toURI().toURL();
		} catch (final MalformedURLException e) {
			logError(e.getMessage());
			return null;
		}
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
		if (prjDir != null) {
			log("projectdir=" + prjDir.getAbsolutePath());

			final File cpFile = new File(prjDir, WEB_CLASS_PATH_FILE);
			if (cpFile.exists()) {
				return loadWebClassPathFile(cpFile);
			}
		}
		return Collections.emptyList();
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
	 * @param cpFile クラスパスファイル
	 * @return 読込行リスト
	 */
	protected List<String> loadWebClassPathFile(final File cpFile) {
		final List<String> rc = new ArrayList<>();
		try (
			FileInputStream fis = new FileInputStream(cpFile);
			InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
			BufferedReader lr = new BufferedReader(reader);
		) {
			String line = null;
			while ((line = lr.readLine()) != null) {
				line = line.replace('\\', '/');
				rc.add(line);
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
