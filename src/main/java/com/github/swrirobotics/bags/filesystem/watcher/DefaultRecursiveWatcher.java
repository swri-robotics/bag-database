/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.swrirobotics.bags.filesystem.watcher;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * The default recursive file watcher monitors a folder (and its sub-folders)
 * by registering a watch on each of the sub-folders. This class is used on
 * Linux/Unix-based operating systems and uses the Java 7 {@link WatchService}.
 *
 * <p>The class walks through the file tree and registers to a watch to every sub-folder.
 * For new folders, a new watch is registered, and stale watches are removed.
 *
 * <p>When a file event occurs, a timer is started to wait for the file operations
 * to settle. It is reset whenever a new event occurs. When the timer times out,
 * an event is thrown through the {@link WatchListener}.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DefaultRecursiveWatcher extends RecursiveWatcher {
	private WatchService watchService;
	private Map<Path, WatchKey> watchPathKeyMap;

	public DefaultRecursiveWatcher(Path root, List<Path> ignorePaths, int settleDelay, WatchListener listener) {
		super(root, ignorePaths, settleDelay, listener);

		this.watchService = null;
		this.watchPathKeyMap = new HashMap<>();
	}

	@Override
	public void beforeStart() throws Exception {
		watchService = FileSystems.getDefault().newWatchService();
	}

	@Override
	protected void beforePollEventLoop() {
		walkTreeAndSetWatches();
	}

	@Override
	protected boolean pollEvents() throws InterruptedException {
		// Take events, but don't care what they are!
		WatchKey watchKey = watchService.take();

		watchKey.pollEvents();
		watchKey.reset();

		// Events are always relevant; ignored paths are not monitored
		return true;
	}

	@Override
	protected void watchEventsOccurred() {
		walkTreeAndSetWatches();
		unregisterStaleWatches();
	}

	@Override
	public void afterStop() throws IOException {
		watchService.close();
	}

	private boolean presentSpecialCharacters(Path dir){
		String dirName = dir.toString();
		Pattern p = Pattern.compile("@.*");
		Matcher m = p.matcher(dirName);
		return m.find();
	}

	private synchronized void walkTreeAndSetWatches() {
		logger.log(Level.INFO, "Registering new folders at watch service ...");

		try {
			Files.walkFileTree(root, new FileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (ignorePaths.contains(dir)||presentSpecialCharacters(dir.getFileName())) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					else {
						registerWatch(dir);
						return FileVisitResult.CONTINUE;
					}
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException e) {
			logger.log(Level.FINE, "IO failed", e);
		}
	}

	private synchronized void unregisterStaleWatches() {
		Set<Path> paths = new HashSet<>(watchPathKeyMap.keySet());
		Set<Path> stalePaths = paths.parallelStream().filter(path -> !Files.exists(path, LinkOption.NOFOLLOW_LINKS))
									.collect(Collectors.toSet());

		if (stalePaths.size() > 0) {
			logger.log(Level.INFO, "Cancelling stale path watches ...");

			for (Path stalePath : stalePaths) {
				unregisterWatch(stalePath);
			}
		}
	}

	private synchronized void registerWatch(Path dir) {
		if (!watchPathKeyMap.containsKey(dir)) {
			logger.log(Level.INFO, "- Registering " + dir);

			try {
				WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
				watchPathKeyMap.put(dir, watchKey);
			}
			catch (IOException e) {
				logger.log(Level.FINE, "IO Failed", e);
			}
		}
	}

	private synchronized void unregisterWatch(Path dir) {
		WatchKey watchKey = watchPathKeyMap.get(dir);

		if (watchKey != null) {
			logger.log(Level.INFO, "- Cancelling " + dir);

			watchKey.cancel();
			watchPathKeyMap.remove(dir);
		}
	}
}
