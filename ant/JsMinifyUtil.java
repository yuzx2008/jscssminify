package net.sf.yuzx.minify.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.Touchable;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.ant.util.ResourceUtils;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class JsMinifyUtil {

	/** Utilities used for file operations */
	private static final FileUtils FILE_UTILS = FileUtils.getFileUtils();

	private String inputEncoding = null;

	private String outputEncoding = null;

	private boolean munge = true;

	boolean preserveAllSemiColons = false;

	boolean disableOptimizations = false;

	boolean verbose = false;

	int lineBreakPos = -1;// 无断行

	static final int BUF_SIZE = 8192;

	public void build(FileResource source, FileResource dest,
			boolean overwrite, boolean preserveLastModified, Project project,
			boolean force) throws IOException {
		if (!(overwrite || SelectorUtils.isOutOfDate(source, dest, FileUtils
				.getFileUtils().getFileTimestampGranularity()))) {
			return;
		}
		File destFile = null;
		if (dest.as(FileProvider.class) != null) {
			destFile = dest.as(FileProvider.class).getFile();
		}
		if (destFile != null && destFile.isFile() && !destFile.canWrite()) {
			if (!force) {
				throw new IOException("can't write to read-only destination "
						+ "file " + destFile);
			} else if (!FILE_UTILS.tryHardToDelete(destFile)) {
				throw new IOException("failed to delete read-only "
						+ "destination file " + destFile);
			}
		}

		if (source.as(FileProvider.class) != null && destFile != null) {
			File sourceFile = source.as(FileProvider.class).getFile();

			File parent = destFile.getParentFile();
			if (parent != null && !parent.isDirectory()
					&& !destFile.getParentFile().mkdirs()) {
				throw new IOException("failed to create the parent directory"
						+ " for " + destFile);
			}

			FileInputStream in = null;
			FileOutputStream out = null;

			try {
				in = new FileInputStream(sourceFile);
				out = new FileOutputStream(destFile);

				compress(in, out);
			} finally {
				FileUtils.close(out);
				FileUtils.close(in);
			}
		}
		if (preserveLastModified) {
			Touchable t = dest.as(Touchable.class);
			if (t != null) {
				ResourceUtils.setLastModified(t, source.getLastModified());
			}
		}
	}

	private void compress(final InputStream is, OutputStream os)
			throws EvaluatorException, IOException {
		Reader in = null;
		Writer out = null;
		try {
			in = new InputStreamReader(is, inputEncoding);
			JavaScriptCompressor compressor = new JavaScriptCompressor(in,
					new ErrorReporter() {
						public void warning(String message, String sourceName,
								int line, String lineSource, int lineOffset) {
							System.err.println("\n[WARNING] message = "
									+ message + " sourceName = " + sourceName
									+ " line = " + line + " lineSource = "
									+ lineSource + " lineOffset = "
									+ lineOffset);
						}

						public void error(String message, String sourceName,
								int line, String lineSource, int lineOffset) {
							throw new Error("\n[ERROR] message = " + message
									+ " sourceName = " + sourceName
									+ " line = " + line + " lineSource = "
									+ lineSource + " lineOffset = "
									+ lineOffset);
						}

						public EvaluatorException runtimeError(String message,
								String sourceName, int line, String lineSource,
								int lineOffset) {
							error(message, sourceName, line, lineSource,
									lineOffset);
							return new EvaluatorException(message);
						}
					});

			out = new OutputStreamWriter(os, outputEncoding);
			compressor.compress(out, lineBreakPos, munge, verbose,
					preserveAllSemiColons, disableOptimizations);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (in != null) {
				try {
					in.close();
					in = null;
				} catch (IOException e) {
				}
			}

			if (out != null) {
				try {
					out.close();
					out = null;
				} catch (IOException e) {
				}
			}
		}
	}

	public void setEncoding(String encoding) {
		this.inputEncoding = encoding;
		if (outputEncoding == null) {
			outputEncoding = encoding;
		}
	}

	public void setOutputEncoding(String encoding) {
		this.outputEncoding = encoding;
	}

	public void setMunge(boolean munge) {
		this.munge = munge;
	}

	public void setPreserveAllSemiColons(boolean preserveAllSemiColons) {
		this.preserveAllSemiColons = preserveAllSemiColons;
	}

	public void setDisableOptimizations(boolean disableOptimizations) {
		this.disableOptimizations = disableOptimizations;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setLineBreakPos(int lineBreakPos) {
		this.lineBreakPos = lineBreakPos;
	}

}
