/*
 * This file is part of Overthere.
 * 
 * Overthere is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Overthere is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Overthere.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.xebialabs.overthere.ssh;

import static com.google.common.collect.Lists.newArrayList;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.xebialabs.overthere.OperatingSystemFamily;
import com.xebialabs.overthere.OverthereFile;
import com.xebialabs.overthere.RuntimeIOException;

/**
 * A file on a host connected through SSH that is accessed using SFTP.
 */
class SshSftpOverthereFile extends SshOverthereFile {

	public SshSftpOverthereFile(SshSftpOverthereConnection connection, String path) {
		super(connection, path);
	}

	protected SftpATTRS stat() throws RuntimeIOException {
		logger.info("Statting file " + this);

		try {
			SftpATTRS attrs = ((SshSftpOverthereConnection) connection).getSharedSftpChannel().stat(convertWindowsPathToWinSshdPath(getPath()));
			return attrs;
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot stat file " + this, exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot stat file " + this, exc);
		}
	}

	public boolean exists() throws RuntimeIOException {
		logger.info("Checking file " + getPath() + " for existence");

		try {
			((SshSftpOverthereConnection) connection).getSharedSftpChannel().stat(convertWindowsPathToWinSshdPath(getPath()));
			return true;
		} catch (SftpException exc) {
			// if we get an SftpException while trying to stat the file, we
			// assume it does not exist
			return false;
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot check existence of file " + getPath(), exc);
		}
	}

	@Override
	public boolean isDirectory() throws RuntimeIOException {
		return stat().isDir();
	}

	@Override
	public long lastModified() {
		return stat().getMTime();
	}

	@Override
	public long length() throws RuntimeIOException {
		return stat().getSize();
	}

	@Override
	public boolean canExecute() throws RuntimeIOException {
		SftpATTRS attrs = stat();
		return (attrs.getPermissions() & 0100) != 0;
	}

	@Override
	public boolean canRead() throws RuntimeIOException {
		SftpATTRS attrs = stat();
		return (attrs.getPermissions() & 0400) != 0;
	}

	@Override
	public boolean canWrite() throws RuntimeIOException {
		SftpATTRS attrs = stat();
		return (attrs.getPermissions() & 0200) != 0;
	}

	@Override
	public List<OverthereFile> listFiles() {
		logger.info("Listing files in " + this);

		try {
			// read files from host
			@SuppressWarnings("unchecked")
			Vector<LsEntry> ls = (Vector<LsEntry>) ((SshSftpOverthereConnection) connection).getSharedSftpChannel().ls(convertWindowsPathToWinSshdPath(getPath()));

			// copy files to list, skipping . and ..
			List<OverthereFile> files = newArrayList();
			for (LsEntry lsEntry : ls) {
				String filename = lsEntry.getFilename();
				if (filename.equals(".") || filename.equals("..")) {
					continue;
				}
				files.add(getFile(filename));
			}
			return files;
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot list directory " + this + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot list directory " + this + ": " + exc.toString(), exc);
		}
	}

	@Override
	public void mkdir() throws RuntimeIOException {
		logger.info("Creating directory " + this);

		try {
			((SshSftpOverthereConnection) connection).getSharedSftpChannel().mkdir(convertWindowsPathToWinSshdPath(getPath()));
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot create directory " + getPath() + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot create directory " + getPath() + ": " + exc.toString(), exc);
		}
	}

	@Override
	public void mkdirs() throws RuntimeIOException {
		logger.info("Creating directories " + this);

		List<OverthereFile> allDirs = new ArrayList<OverthereFile>();
		OverthereFile dir = this;
		do {
			allDirs.add(0, dir);
		} while ((dir = dir.getParentFile()) != null);

		for (OverthereFile each : allDirs) {
			if (!each.exists()) {
				each.mkdir();
			}
		}
	}

	@Override
	public void renameTo(OverthereFile dest) {
		logger.info("Renaming " + this + " to " + dest);

		if (dest instanceof SshSftpOverthereFile) {
			SshSftpOverthereFile sftpDest = (SshSftpOverthereFile) dest;
			if (sftpDest.getConnection() == getConnection()) {
				try {
					((SshSftpOverthereConnection) connection).getSharedSftpChannel().rename(getPath(), sftpDest.getPath());
				} catch (SftpException exc) {
					throw new RuntimeIOException("Cannot move/rename file/directory " + this + " to " + dest + ": " + exc.toString(), exc);
				} catch (JSchException exc) {
					throw new RuntimeIOException("Cannot move/rename file/directory " + this + " to " + dest + ": " + exc.toString(), exc);
				}
			} else {
				throw new RuntimeIOException("Cannot move/rename SSH/SCP file/directory " + this + " to SSH/SCP file/directory " + dest
				        + " because it is in a different connection");
			}
		} else {
			throw new RuntimeIOException("Cannot move/rename SSH/SCP file/directory " + this + " to non-SSH/SCP file/directory " + dest);
		}
	}

	@Override
	protected void deleteFile() {
		logger.info("Removing file " + this);

		try {
			((SshSftpOverthereConnection) connection).getSharedSftpChannel().rm(convertWindowsPathToWinSshdPath(getPath()));
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot delete file " + this + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot delete file " + this + ": " + exc.toString(), exc);
		}
	}

	@Override
	protected void deleteDirectory() {
		logger.info("Removing directory " + this);

		try {
			((SshSftpOverthereConnection) connection).getSharedSftpChannel().rmdir(convertWindowsPathToWinSshdPath(getPath()));
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot delete directory " + this + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot delete directory " + this + ": " + exc.toString(), exc);
		}
	}

	@Override
	public InputStream getInputStream() {
		logger.info("Opening SFTP input stream to read from file " + this);

		try {
			ChannelSftp sftpChannel = ((SshSftpOverthereConnection) connection).openSftpChannel(false);
			InputStream in = new SshSftpInputStream(this, sftpChannel, sftpChannel.get(convertWindowsPathToWinSshdPath(getPath())));
			return in;
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot read from file " + getPath() + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot read from file " + getPath() + ": " + exc.toString(), exc);
		}
	}

	@Override
	public OutputStream getOutputStream(long length) throws RuntimeIOException {
		logger.info("Opening SFTP ouput stream to write to file " + this);

		try {
			ChannelSftp sftpChannel = ((SshSftpOverthereConnection) connection).openSftpChannel(false);
			OutputStream out = new SshSftpOutputStream(this, sftpChannel, sftpChannel.put(convertWindowsPathToWinSshdPath(getPath())));
			return out;
		} catch (SftpException exc) {
			throw new RuntimeIOException("Cannot write to file " + getPath() + ": " + exc.toString(), exc);
		} catch (JSchException exc) {
			throw new RuntimeIOException("Cannot write to file " + getPath() + ": " + exc.toString(), exc);
		}
	}

	/**
	 * TODO: Do we still want to support WinSSHD? What about copssh?
	 */
	private String convertWindowsPathToWinSshdPath(String path) {
		if (connection.getHostOperatingSystem() == OperatingSystemFamily.WINDOWS) {
			String winSshdPath;
			if (path.length() == 2 && path.charAt(1) == ':') {
				winSshdPath = "/" + path.charAt(0);
			} else if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
				winSshdPath = "/" + path.replace('\\', '/').replace(":", "");
			} else {
				winSshdPath = path;
			}
			if (logger.isDebugEnabled())
				logger.debug("Translated Windows path " + path + " to WinSSHD path " + winSshdPath);
			path = winSshdPath;
		}
		return path;
	}

	private static Logger logger = LoggerFactory.getLogger(SshSftpOverthereFile.class);

}