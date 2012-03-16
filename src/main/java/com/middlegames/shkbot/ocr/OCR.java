package com.middlegames.shkbot.ocr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.sikuli.script.Region;


/**
 * Simple OCR tool. It recognize only one line strings and require glyphs
 * library to be prepared first.
 * 
 * @author Middle Gamer (middlegamer)
 */
public class OCR {

	private static class MatchesComparator implements Comparator<Match> {

		@Override
		public int compare(Match a, Match b) {
			if (a.x < b.x) {
				return -1;
			} else if (a.x > b.x) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	private static class Matcher implements Callable<List<Match>> {

		private Glyph glyph = null;
		private Region region = null;
		private Map<Match, Glyph> mapping = null;
		private CountDownLatch latch = null;

		public Matcher(Glyph glyph, Region region, Map<Match, Glyph> mapping, CountDownLatch latch) {
			this.glyph = glyph;
			this.region = region;
			this.mapping = mapping;
			this.latch = latch;
		}

		@Override
		public List<Match> call() throws Exception {
			try {

				List<Match> matches = new ArrayList<>();
				Iterator<Match> all = region.findAllNow(glyph.getPattern());

				while (all.hasNext()) {
					Match m = all.next();
					matches.add(m);
					mapping.put(m, glyph);
				}

				return matches;

			} finally {
				latch.countDown();
			}
		}
	}

	private static class MatcherThreadFactory implements ThreadFactory {

		private int i = 0;

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "matcher-thread-" + ++i);
			t.setDaemon(true);
			return t;
		}
	}

	private static ExecutorService executor = Executors.newCachedThreadPool(new MatcherThreadFactory());

	private List<Glyph> glyphs = null;

	private OCR(List<Glyph> glyphs) {
		this.glyphs = glyphs;
	}

	public static OCR getSpec(String name) {
		List<Glyph> glyphs = Glyphs.getInstance().load("data/glyphs/" + name);
		return new OCR(glyphs);
	}

	public String read(Region region) {

		region.setThrowException(false);

		Map<Match, Glyph> mapping = new HashMap<>();
		List<Match> matches = new ArrayList<>();

		List<FutureTask<List<Match>>> futures = new ArrayList<>();
		CountDownLatch latch = new CountDownLatch(glyphs.size());

		for (Glyph g : glyphs) {
			FutureTask<List<Match>> future = new FutureTask<>(new Matcher(g, region, mapping, latch));
			futures.add(future);
			executor.execute(future);
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		for (FutureTask<List<Match>> future : futures) {
			try {
				matches.addAll(future.get());
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		Collections.sort(matches, new MatchesComparator());

		StringBuilder sb = new StringBuilder();
		for (Match m : matches) {
			sb.append(mapping.get(m).getCharacter());
		}

		return sb.toString();
	}

	public static void main(String[] args) throws InterruptedException, FindFailed {
		Region region = new Region(0, 0, 700, 600);
		region.highlight(5.0f);
		OCR ocr = OCR.getSpec("numbers");
		System.out.println(ocr.read(region));
	}
}
