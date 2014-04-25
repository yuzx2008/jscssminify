package net.sf.yuzx.minify.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Mapper;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.util.FileNameMapper;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.ant.util.FlatFileNameMapper;
import org.apache.tools.ant.util.IdentityMapper;
import org.apache.tools.ant.util.LinkedHashtable;
import org.apache.tools.ant.util.SourceFileScanner;

public class CssMinifyTask extends Task {

	protected boolean failonerror = true;

	private boolean quiet = false;

	protected Hashtable<String, String[]> fileCopyMap = new LinkedHashtable<String, String[]>();

	protected Hashtable<String, String[]> dirCopyMap = new LinkedHashtable<String, String[]>();

	protected Vector<ResourceCollection> rcs = new Vector<ResourceCollection>();

	static final File NULL_FILE_PLACEHOLDER = new File("/NULL_FILE");

	static final String LINE_SEPARATOR = System.getProperty("line.separator");

	protected File destDir = null; // the destination directory

	protected boolean includeEmpty = true;

	protected Mapper mapperElement = null;

	protected boolean forceOverwrite = false;

	private long granularity = 0;

	protected FileUtils fileUtils;

	private boolean enableMultipleMappings = false;

	protected boolean flatten = false;

	protected int verbosity = Project.MSG_VERBOSE;

	protected boolean preserveLastModified = false;

	private boolean force = false;

	private CssMinifyUtil cssMinifyUtil;

	/**
	 * Copy task constructor.
	 */
	public CssMinifyTask() {
		fileUtils = FileUtils.getFileUtils();
		cssMinifyUtil = new CssMinifyUtil();
		granularity = fileUtils.getFileTimestampGranularity();
	}

	@Override
	public void execute() {
		HashMap<File, List<String>> filesByBasedir = new HashMap<File, List<String>>();
		HashMap<File, List<String>> dirsByBasedir = new HashMap<File, List<String>>();
		HashSet<File> baseDirs = new HashSet<File>();
		final int size = rcs.size();
		for (int i = 0; i < size; i++) {
			ResourceCollection rc = rcs.elementAt(i);

			// Step (1) - beware of the ZipFileSet
			if (rc instanceof FileSet && rc.isFilesystemOnly()) {
				FileSet fs = (FileSet) rc;
				DirectoryScanner ds = null;
				try {
					ds = fs.getDirectoryScanner(getProject());
				} catch (BuildException e) {
					if (failonerror
							|| !getMessage(e).endsWith(
									DirectoryScanner.DOES_NOT_EXIST_POSTFIX)) {
						throw e;
					} else {
						if (!quiet) {
							log("Warning: " + getMessage(e), Project.MSG_ERR);
						}
						continue;
					}
				}
				File fromDir = fs.getDir(getProject());

				String[] srcFiles = ds.getIncludedFiles();
				String[] srcDirs = ds.getIncludedDirectories();

				add(fromDir, srcFiles, filesByBasedir);
				add(fromDir, srcDirs, dirsByBasedir);
				baseDirs.add(fromDir);
			}
		}

		iterateOverBaseDirs(baseDirs, dirsByBasedir, filesByBasedir);

		// do all the copy operations now...
		try {
			doFileOperations();
		} catch (BuildException e) {
			if (!failonerror) {
				if (!quiet) {
					log("Warning: " + getMessage(e), Project.MSG_ERR);
				}
			} else {
				throw e;
			}
		}
	}

	protected void doFileOperations() {
		if (fileCopyMap.size() > 0) {
			log("Copying " + fileCopyMap.size() + " file"
					+ (fileCopyMap.size() == 1 ? "" : "s") + " to "
					+ destDir.getAbsolutePath());

			for (Map.Entry<String, String[]> e : fileCopyMap.entrySet()) {
				String fromFile = e.getKey();
				String[] toFiles = e.getValue();

				for (int i = 0; i < toFiles.length; i++) {
					String toFile = toFiles[i];

					if (fromFile.equals(toFile)) {
						log("Skipping self-copy of " + fromFile, verbosity);
						continue;
					}

					try {
						log("Copying " + fromFile + " to " + toFile, verbosity);

						cssMinifyUtil.build(
								new FileResource(new File(fromFile)),
								new FileResource(new File(toFile)),
								forceOverwrite, preserveLastModified,
								getProject(), getForce());

					} catch (IOException ioe) {
						String msg = "Failed to copy " + fromFile + " to "
								+ toFile + " due to " + getDueTo(ioe);
						File targetFile = new File(toFile);
						if (targetFile.exists() && !targetFile.delete()) {
							msg += " and I couldn't delete the corrupt "
									+ toFile;
						}
						if (failonerror) {
							throw new BuildException(msg, ioe, getLocation());
						}
						log(msg, Project.MSG_ERR);
					}
				}
			}
		}
		if (includeEmpty) {
			int createCount = 0;
			for (String[] dirs : dirCopyMap.values()) {
				for (int i = 0; i < dirs.length; i++) {
					File d = new File(dirs[i]);
					if (!d.exists()) {
						if (!d.mkdirs()) {
							log("Unable to create directory "
									+ d.getAbsolutePath(), Project.MSG_ERR);
						} else {
							createCount++;
						}
					}
				}
			}
			if (createCount > 0) {
				log("Copied " + dirCopyMap.size() + " empty director"
						+ (dirCopyMap.size() == 1 ? "y" : "ies") + " to "
						+ createCount + " empty director"
						+ (createCount == 1 ? "y" : "ies") + " under "
						+ destDir.getAbsolutePath());
			}
		}
	}

	private String getDueTo(Exception ex) {
		boolean baseIOException = ex.getClass() == IOException.class;
		StringBuffer message = new StringBuffer();
		if (!baseIOException || ex.getMessage() == null) {
			message.append(ex.getClass().getName());
		}
		if (ex.getMessage() != null) {
			if (!baseIOException) {
				message.append(" ");
			}
			message.append(ex.getMessage());
		}
		if (ex.getClass().getName().indexOf("MalformedInput") != -1) {
			message.append(LINE_SEPARATOR);
			message.append("This is normally due to the input file containing invalid");
			message.append(LINE_SEPARATOR);
		}
		return message.toString();
	}

	public void setTodir(File destDir) {
		this.destDir = destDir;
	}

	private void iterateOverBaseDirs(HashSet<File> baseDirs,
			HashMap<File, List<String>> dirsByBasedir,
			HashMap<File, List<String>> filesByBasedir) {

		for (File f : baseDirs) {
			List<String> files = filesByBasedir.get(f);
			List<String> dirs = dirsByBasedir.get(f);

			String[] srcFiles = new String[0];
			if (files != null) {
				srcFiles = files.toArray(srcFiles);
			}
			String[] srcDirs = new String[0];
			if (dirs != null) {
				srcDirs = dirs.toArray(srcDirs);
			}
			scan(f == NULL_FILE_PLACEHOLDER ? null : f, destDir, srcFiles,
					srcDirs);
		}
	}

	protected void buildMap(File fromDir, File toDir, String[] names,
			FileNameMapper mapper, Hashtable<String, String[]> map) {
		String[] toCopy = null;
		if (forceOverwrite) {
			Vector<String> v = new Vector<String>();
			for (int i = 0; i < names.length; i++) {
				if (mapper.mapFileName(names[i]) != null) {
					v.addElement(names[i]);
				}
			}
			toCopy = new String[v.size()];
			v.copyInto(toCopy);
		} else {
			SourceFileScanner ds = new SourceFileScanner(this);
			toCopy = ds.restrict(names, fromDir, toDir, mapper, granularity);
		}
		for (int i = 0; i < toCopy.length; i++) {
			File src = new File(fromDir, toCopy[i]);
			String[] mappedFiles = mapper.mapFileName(toCopy[i]);

			if (!enableMultipleMappings) {
				map.put(src.getAbsolutePath(), new String[] { new File(toDir,
						mappedFiles[0]).getAbsolutePath() });
			} else {
				// reuse the array created by the mapper
				for (int k = 0; k < mappedFiles.length; k++) {
					mappedFiles[k] = new File(toDir, mappedFiles[k])
							.getAbsolutePath();
				}
				map.put(src.getAbsolutePath(), mappedFiles);
			}
		}
	}

	protected void scan(File fromDir, File toDir, String[] files, String[] dirs) {
		FileNameMapper mapper = getMapper();
		buildMap(fromDir, toDir, files, mapper, fileCopyMap);

		if (includeEmpty) {
			buildMap(fromDir, toDir, dirs, mapper, dirCopyMap);
		}
	}

	public Mapper createMapper() throws BuildException {
		if (mapperElement != null) {
			throw new BuildException("Cannot define more than one mapper",
					getLocation());
		}
		mapperElement = new Mapper(getProject());
		return mapperElement;
	}

	public void add(FileNameMapper fileNameMapper) {
		createMapper().add(fileNameMapper);
	}

	private FileNameMapper getMapper() {
		FileNameMapper mapper = null;
		if (mapperElement != null) {
			mapper = mapperElement.getImplementation();
		} else if (flatten) {
			mapper = new FlatFileNameMapper();
		} else {
			mapper = new IdentityMapper();
		}
		return mapper;
	}

	private static File getKeyFile(File f) {
		return f == null ? NULL_FILE_PLACEHOLDER : f;
	}

	private static void add(File baseDir, String[] names,
			Map<File, List<String>> m) {
		if (names != null) {
			baseDir = getKeyFile(baseDir);
			List<String> l = m.get(baseDir);
			if (l == null) {
				l = new ArrayList<String>(names.length);
				m.put(baseDir, l);
			}
			l.addAll(java.util.Arrays.asList(names));
		}
	}

	private String getMessage(Exception ex) {
		return ex.getMessage() == null ? ex.toString() : ex.getMessage();
	}

	public void setLineBreakPos(int lineBreakPos) {
		cssMinifyUtil.setLineBreakPos(lineBreakPos);
	}

	public void addFileset(FileSet set) {
		add(set);
	}

	public void add(ResourceCollection res) {
		rcs.add(res);
	}

	public void setFailOnError(boolean failonerror) {
		this.failonerror = failonerror;
	}

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	public void setOverwrite(boolean overwrite) {
		this.forceOverwrite = overwrite;
	}

	public void setEnableMultipleMappings(boolean enableMultipleMappings) {
		this.enableMultipleMappings = enableMultipleMappings;
	}

	public boolean isEnableMultipleMapping() {
		return enableMultipleMappings;
	}

	public void setFlatten(boolean flatten) {
		this.flatten = flatten;
	}

	public void setVerbose(boolean verbose) {
		this.verbosity = verbose ? Project.MSG_INFO : Project.MSG_VERBOSE;
	}

	public void setEncoding(String encoding) {
		cssMinifyUtil.setEncoding(encoding);
	}

	public void setOutputEncoding(String encoding) {
		cssMinifyUtil.setOutputEncoding(encoding);
	}

	public void setPreserveLastModified(boolean preserve) {
		preserveLastModified = preserve;
	}

	public boolean getPreserveLastModified() {
		return preserveLastModified;
	}

	public void setForce(boolean f) {
		force = f;
	}

	public boolean getForce() {
		return force;
	}

}
