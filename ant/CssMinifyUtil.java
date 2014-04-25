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

import com.yahoo.platform.yui.compressor.CssCompressor;

public class CssMinifyUtil {

	private static final FileUtils FILE_UTILS = FileUtils.getFileUtils();

	private String inputEncoding = null;

	private String outputEncoding = null;

	boolean verbose = false;

	// 无断行
	int lineBreakPos = -1;

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

	private void compress(InputStream is, OutputStream os) {
		Reader in = null;
		Writer out = null;
		try {
			in = new InputStreamReader(is, inputEncoding);
			CssCompressor compressor = new CssCompressor(in);

			out = new OutputStreamWriter(os, outputEncoding);
			compressor.compress(out, lineBreakPos);
			out.flush();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				try {
					is.close();
					is = null;
				} catch (IOException e) {
				}
			}
			if (in != null) {
				try {
					in.close();
					in = null;
				} catch (IOException e) {
				}
			}

			if (os != null) {
				try {
					os.close();
					os = null;
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

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setLineBreakPos(int lineBreakPos) {
		this.lineBreakPos = lineBreakPos;
	}

}
