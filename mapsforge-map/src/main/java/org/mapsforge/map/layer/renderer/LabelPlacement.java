/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.layer.renderer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.model.Tile;

/**
 * This class place the labels form POIs, area labels and normal labels. The main target is avoiding collisions of these
 * different labels.
 */
class LabelPlacement {
	/**
	 * This class holds the reference positions for the two and four point greedy algorithms.
	 */
	static class ReferencePosition {
		double height;
		final int nodeNumber;
		double width;
		final double x;
		final double y;

		ReferencePosition(double x, double y, int nodeNumber, double width, double height) {
			this.x = x;
			this.y = y;
			this.nodeNumber = nodeNumber;
			this.width = width;
			this.height = height;
		}
	}

	static final class ReferencePositionHeightComparator implements Comparator<ReferencePosition>, Serializable {
		static final ReferencePositionHeightComparator INSTANCE = new ReferencePositionHeightComparator();
		private static final long serialVersionUID = 1L;

		private ReferencePositionHeightComparator() {
			// do nothing
		}

		@Override
		public int compare(ReferencePosition x, ReferencePosition y) {
			if (x.y - x.height < y.y - y.height) {
				return -1;
			}

			if (x.y - x.height > y.y - y.height) {
				return 1;
			}
			return 0;
		}
	}

	static final class ReferencePositionWidthComparator implements Comparator<ReferencePosition>, Serializable {
		static final ReferencePositionWidthComparator INSTANCE = new ReferencePositionWidthComparator();
		private static final long serialVersionUID = 1L;

		private ReferencePositionWidthComparator() {
			// do nothing
		}

		@Override
		public int compare(ReferencePosition x, ReferencePosition y) {
			if (x.x + x.width < y.x + y.width) {
				return -1;
			}

			if (x.x + x.width > y.x + y.width) {
				return 1;
			}

			return 0;
		}
	}

	static final class ReferencePositionXComparator implements Comparator<ReferencePosition>, Serializable {
		static final ReferencePositionXComparator INSTANCE = new ReferencePositionXComparator();
		private static final long serialVersionUID = 1L;

		private ReferencePositionXComparator() {
			// do nothing
		}

		@Override
		public int compare(ReferencePosition x, ReferencePosition y) {
			if (x.x < y.x) {
				return -1;
			}

			if (x.x > y.x) {
				return 1;
			}

			return 0;
		}
	}

	static final class ReferencePositionYComparator implements Comparator<ReferencePosition>, Serializable {
		static final ReferencePositionYComparator INSTANCE = new ReferencePositionYComparator();
		private static final long serialVersionUID = 1L;

		private ReferencePositionYComparator() {
			// do nothing
		}

		@Override
		public int compare(ReferencePosition x, ReferencePosition y) {
			if (x.y < y.y) {
				return -1;
			}

			if (x.y > y.y) {
				return 1;
			}

			return 0;
		}
	}

	private static final int LABEL_DISTANCE_TO_LABEL = 2;
	private static final int LABEL_DISTANCE_TO_SYMBOL = 2;
	private static final int START_DISTANCE_TO_SYMBOLS = 4;
	private static final int SYMBOL_DISTANCE_TO_SYMBOL = 2;

	/**
	 * The inputs are all the label and symbol objects of the current object. The output is overlap free label and
	 * symbol placement with the greedy strategy. The placement model is either the two fixed point or the four fixed
	 * point model.
	 * 
	 * @param labels
	 *            labels from the current object.
	 * @param symbols
	 *            symbols of the current object.
	 * @param areaLabels
	 *            area labels from the current object.
	 * @param cT
	 *            current object with the x,y- coordinates and the zoom level.
	 * @return the processed list of labels.
	 */
	static List<PointTextContainer> placeLabels(List<PointTextContainer> labels, List<SymbolContainer> symbols,
			List<PointTextContainer> areaLabels, DependencyCache dependencyCache) {
		List<PointTextContainer> returnLabels = labels;

		preprocessAreaLabels(areaLabels);
		if (!areaLabels.isEmpty()) {
			dependencyCache.removeAreaLabelsInAlreadyDrawnAreas(areaLabels);
		}

		preprocessLabels(returnLabels);

		preprocessSymbols(symbols);
		dependencyCache.removeSymbolsFromDrawnAreas(symbols);

		removeEmptySymbolReferences(returnLabels, symbols);

		removeOverlappingSymbolsWithAreaLabels(symbols, areaLabels);

		dependencyCache.removeOverlappingObjectsWithDependencyOnTile(returnLabels, areaLabels, symbols);

		if (!returnLabels.isEmpty()) {
			returnLabels = processFourPointGreedy(returnLabels, symbols, areaLabels, dependencyCache);
		}

		dependencyCache.fillDependencyOnTile(returnLabels, symbols, areaLabels);

		return returnLabels;
	}

	/**
	 * Centers the labels.
	 * 
	 * @param labels
	 *            labels to center
	 */
	private static void centerLabels(List<PointTextContainer> labels) {
		for (int i = 0; i < labels.size(); i++) {
			final PointTextContainer label = labels.get(i);
			label.x = label.x - label.boundary.getWidth() / 2;
		}
	}

	private static void preprocessAreaLabels(List<PointTextContainer> areaLabels) {
		centerLabels(areaLabels);

		removeOutOfTileAreaLabels(areaLabels);

		removeOverlappingAreaLabels(areaLabels);
	}

	private static void preprocessLabels(List<PointTextContainer> labels) {
		removeOutOfTileLabels(labels);
	}

	private static void preprocessSymbols(List<SymbolContainer> symbols) {
		removeOutOfTileSymbols(symbols);
		removeOverlappingSymbols(symbols);
	}

	/**
	 * This method uses an adapted greedy strategy for the fixed four position model, above, under left and right form
	 * the point of interest. It uses no priority search tree, because it will not function with symbols only with
	 * points. Instead it uses two minimum heaps. They work similar to a sweep line algorithm but have not a O(n log n
	 * +k) runtime. To find the rectangle that has the top edge, I use also a minimum Heap. The rectangles are sorted by
	 * their y coordinates.
	 * 
	 * @param labels
	 *            label positions and text
	 * @param symbols
	 *            symbol positions
	 * @param areaLabels
	 *            area label positions and text
	 * @param dependencyCache 
	 * @return list of labels without overlaps with symbols and other labels by the four fixed position greedy strategy
	 */
	private static List<PointTextContainer> processFourPointGreedy(List<PointTextContainer> labels,
			List<SymbolContainer> symbols, List<PointTextContainer> areaLabels, DependencyCache dependencyCache) {
		// Array for the generated reference positions around the points of interests
		ReferencePosition[] refPos = new ReferencePosition[(labels.size()) * 4];

		final int distance = START_DISTANCE_TO_SYMBOLS;

		// creates the reference positions
		for (int z = 0; z < labels.size(); z++) {
				final PointTextContainer tmp = labels.get(z);
				if (tmp.symbol != null) {

					// up
					refPos[z * 4] = new ReferencePosition(tmp.x - tmp.boundary.getWidth() / 2, tmp.y
							- tmp.symbol.symbol.getHeight() / 2 - distance, z, tmp.boundary.getWidth(),
							tmp.boundary.getHeight());
					// down
					refPos[z * 4 + 1] = new ReferencePosition(tmp.x - tmp.boundary.getWidth() / 2, tmp.y
							+ tmp.symbol.symbol.getHeight() / 2 + tmp.boundary.getHeight() + distance, z,
							tmp.boundary.getWidth(), tmp.boundary.getHeight());
					// left
					refPos[z * 4 + 2] = new ReferencePosition(tmp.x - tmp.symbol.symbol.getWidth() / 2
							- tmp.boundary.getWidth() - distance, tmp.y + tmp.boundary.getHeight() / 2, z,
							tmp.boundary.getWidth(), tmp.boundary.getHeight());
					// right
					refPos[z * 4 + 3] = new ReferencePosition(tmp.x + tmp.symbol.symbol.getWidth() / 2 + distance, tmp.y
							+ tmp.boundary.getHeight() / 2 - 0.1f, z, tmp.boundary.getWidth(), tmp.boundary.getHeight());
				} else {
					refPos[z * 4] = new ReferencePosition(tmp.x - ((tmp.boundary.getWidth()) / 2),
							tmp.y, z, tmp.boundary.getWidth(), tmp.boundary.getHeight());
					refPos[z * 4 + 1] = null;
					refPos[z * 4 + 2] = null;
					refPos[z * 4 + 3] = null;
				}
		}

		removeNonValidateReferencePositionSymbols(refPos, symbols);
		removeNonValidateReferencePositionAreaLabels(refPos, areaLabels);
		dependencyCache.removeOutOfTileReferencePoints(refPos);
		dependencyCache.removeOverlappingLabels(refPos);
		dependencyCache.removeOverlappingSymbols(refPos);

		// lists that sorts the reference points after the minimum top edge y position
		PriorityQueue<ReferencePosition> priorUp = new PriorityQueue<ReferencePosition>(labels.size() * 4 * 2
				+ labels.size() / 10 * 2, ReferencePositionYComparator.INSTANCE);
		// lists that sorts the reference points after the minimum bottom edge y position
		PriorityQueue<ReferencePosition> priorDown = new PriorityQueue<ReferencePosition>(labels.size() * 4 * 2
				+ labels.size() / 10 * 2, ReferencePositionHeightComparator.INSTANCE);

		// do while it gives reference positions
		for (int i = 0; i < refPos.length; i++) {
			final ReferencePosition referencePosition = refPos[i];
			if (referencePosition != null) {
				priorUp.add(referencePosition);
				priorDown.add(referencePosition);
			}
		}

		List<PointTextContainer> resolutionSet = new ArrayList<PointTextContainer>();

		while (priorUp.size() != 0) {
			final ReferencePosition referencePosition = priorUp.remove();

			final PointTextContainer label = labels.get(referencePosition.nodeNumber);

			resolutionSet.add(new PointTextContainer(label.text, referencePosition.x,
					referencePosition.y, label.paintFront, label.paintBack, label.symbol));

			if (priorUp.size() == 0) {
				return resolutionSet;
			}

			priorUp.remove(refPos[referencePosition.nodeNumber * 4 + 0]);
			priorUp.remove(refPos[referencePosition.nodeNumber * 4 + 1]);
			priorUp.remove(refPos[referencePosition.nodeNumber * 4 + 2]);
			priorUp.remove(refPos[referencePosition.nodeNumber * 4 + 3]);

			priorDown.remove(refPos[referencePosition.nodeNumber * 4 + 0]);
			priorDown.remove(refPos[referencePosition.nodeNumber * 4 + 1]);
			priorDown.remove(refPos[referencePosition.nodeNumber * 4 + 2]);
			priorDown.remove(refPos[referencePosition.nodeNumber * 4 + 3]);

			LinkedList<ReferencePosition> linkedRef = new LinkedList<ReferencePosition>();

			while (priorDown.size() != 0) {
				if (priorDown.peek().x < referencePosition.x + referencePosition.width) {
					linkedRef.add(priorDown.remove());
				} else {
					break;
				}
			}
			// brute Force collision test (faster then sweep line for a small amount of
			// objects)
			for (int i = 0; i < linkedRef.size(); i++) {
				if ((linkedRef.get(i).x <= referencePosition.x + referencePosition.width)
						&& (linkedRef.get(i).y >= referencePosition.y - linkedRef.get(i).height)
						&& (linkedRef.get(i).y <= referencePosition.y + linkedRef.get(i).height)) {
					priorUp.remove(linkedRef.get(i));
					linkedRef.remove(i);
					i--;
				}
			}
			priorDown.addAll(linkedRef);
		}

		return resolutionSet;
	}

	private static void removeEmptySymbolReferences(List<PointTextContainer> nodes, List<SymbolContainer> symbols) {
		for (int i = 0; i < nodes.size(); i++) {
			final PointTextContainer label = nodes.get(i);
			if (!symbols.contains(label.symbol)) {
				label.symbol = null;
			}
		}
	}

	/**
	 * The greedy algorithms need possible label positions, to choose the best among them. This method removes the
	 * reference points, that are not validate. Not validate means, that the Reference overlap with another symbol or
	 * label or is outside of the object.
	 * 
	 * @param refPos
	 *            list of the potential positions
	 * @param symbols
	 *            actual list of the symbols
	 */
	private static void removeNonValidateReferencePositionSymbols(ReferencePosition[] refPos, List<SymbolContainer> symbols) {
		final int distance = LABEL_DISTANCE_TO_SYMBOL;

		for (int i = 0; i < symbols.size(); i++) {
			final SymbolContainer symbolContainer = symbols.get(i);
			final Rectangle rect1 = new Rectangle((int) symbolContainer.point.x - distance,
					(int) symbolContainer.point.y - distance, (int) symbolContainer.point.x
							+ symbolContainer.symbol.getWidth() + distance, (int) symbolContainer.point.y
							+ symbolContainer.symbol.getHeight() + distance);

			for (int y = 0; y < refPos.length; y++) {
				if (refPos[y] != null) {
					final Rectangle rect2 = new Rectangle((int) refPos[y].x, (int) (refPos[y].y - refPos[y].height),
							(int) (refPos[y].x + refPos[y].width), (int) (refPos[y].y));

					if (rect2.intersects(rect1)) {
						refPos[y] = null;
					}
				}
			}
		}
	}

	/**
	 * The greedy algorithms need possible label positions, to choose the best among them. This method removes the
	 * reference points, that are not validate. Not validate means, that the Reference overlap with another symbol or
	 * label or is outside of the object.
	 * 
	 * @param refPos
	 *            list of the potential positions
	 * @param areaLabels
	 *            actual list of the area labels
	 */
	private static void removeNonValidateReferencePositionAreaLabels(ReferencePosition[] refPos, List<PointTextContainer> areaLabels) {
		final int distance = LABEL_DISTANCE_TO_LABEL;

		for (PointTextContainer areaLabel : areaLabels) {
			final Rectangle rect1 = new Rectangle((int) areaLabel.x - distance, (int) areaLabel.y - areaLabel.boundary.getHeight()
					- distance, (int) areaLabel.x + areaLabel.boundary.getWidth() + distance, (int) areaLabel.y
					+ distance);

			for (int y = 0; y < refPos.length; y++) {
				if (refPos[y] != null) {
					final Rectangle rect2 = new Rectangle((int) refPos[y].x, (int) (refPos[y].y - refPos[y].height),
							(int) (refPos[y].x + refPos[y].width), (int) (refPos[y].y));

					if (rect2.intersects(rect1)) {
						refPos[y] = null;
					}
				}
			}
		}
	}

	/**
	 * This method removes the area labels, that are not visible in the actual object.
	 * 
	 * @param areaLabels
	 *            area Labels from the actual object
	 */
	private static void removeOutOfTileAreaLabels(List<PointTextContainer> areaLabels) {
		for (int i = 0; i < areaLabels.size(); i++) {
			final PointTextContainer label = areaLabels.get(i);

			if (label.x > Tile.TILE_SIZE) {
				areaLabels.remove(i);

				i--;
			} else if (label.y - label.boundary.getHeight() > Tile.TILE_SIZE) {
				areaLabels.remove(i);

				i--;
			} else if (label.x + label.boundary.getWidth() < 0.0f) {
				areaLabels.remove(i);

				i--;
			} else if (label.y + label.boundary.getHeight() < 0.0f) {
				areaLabels.remove(i);

				i--;
			}
		}
	}

	/**
	 * This method removes the labels, that are not visible in the actual object.
	 * 
	 * @param labels
	 *            Labels from the actual object
	 */
	private static void removeOutOfTileLabels(List<PointTextContainer> labels) {
		for (int i = 0; i < labels.size();) {
			final PointTextContainer label = labels.get(i);

			if (label.x - label.boundary.getWidth() / 2 > Tile.TILE_SIZE) {
				labels.remove(i);
			} else if (label.y - label.boundary.getHeight() > Tile.TILE_SIZE) {
				labels.remove(i);
			} else if ((label.x - label.boundary.getWidth() / 2 + label.boundary.getWidth()) < 0.0f) {
				labels.remove(i);
			} else if (label.y < 0.0f) {
				labels.remove(i);
			} else {
				i++;
			}
		}
	}

	/**
	 * This method removes the Symbols, that are not visible in the actual object.
	 * 
	 * @param symbols
	 *            Symbols from the actual object
	 */
	private static void removeOutOfTileSymbols(List<SymbolContainer> symbols) {
		for (int i = 0; i < symbols.size();) {
			final SymbolContainer symbolContainer = symbols.get(i);

			if (symbolContainer.point.x > Tile.TILE_SIZE) {
				symbols.remove(i);
			} else if (symbolContainer.point.y > Tile.TILE_SIZE) {
				symbols.remove(i);
			} else if (symbolContainer.point.x + symbolContainer.symbol.getWidth() < 0.0f) {
				symbols.remove(i);
			} else if (symbolContainer.point.y + symbolContainer.symbol.getHeight() < 0.0f) {
				symbols.remove(i);
			} else {
				i++;
			}
		}
	}

	/**
	 * This method removes all the area labels, that overlap each other. So that the output is collision free
	 * 
	 * @param areaLabels
	 *            area labels from the actual object
	 */
	private static void removeOverlappingAreaLabels(List<PointTextContainer> areaLabels) {
		int dis = LABEL_DISTANCE_TO_LABEL;

		for (int x = 0; x < areaLabels.size(); x++) {
			final PointTextContainer label1 = areaLabels.get(x);
			final Rectangle rect1 = new Rectangle((int) label1.x - dis, (int) label1.y - dis,
					(int) (label1.x + label1.boundary.getWidth()) + dis, (int) (label1.y
							+ label1.boundary.getHeight() + dis));

			for (int y = x + 1; y < areaLabels.size(); y++) {
				if (y != x) {
					final PointTextContainer label2 = areaLabels.get(y);
					final Rectangle rect2 = new Rectangle((int) label2.x, (int) label2.y,
							(int) (label2.x + label2.boundary.getWidth()),
							(int) (label2.y + label2.boundary.getHeight()));

					if (rect1.intersects(rect2)) {
						areaLabels.remove(y);

						y--;
					}
				}
			}
		}
	}

	/**
	 * This method removes all the Symbols, that overlap each other. So that the output is collision free.
	 * 
	 * @param symbols
	 *            symbols from the actual object
	 */
	private static void removeOverlappingSymbols(List<SymbolContainer> symbols) {
		int dis = SYMBOL_DISTANCE_TO_SYMBOL;

		for (int x = 0; x < symbols.size(); x++) {
			final SymbolContainer symbolContainer1 = symbols.get(x);
			final Rectangle rect1 = new Rectangle((int) symbolContainer1.point.x - dis, (int) symbolContainer1.point.y
					- dis, (int) symbolContainer1.point.x + symbolContainer1.symbol.getWidth() + dis,
					(int) symbolContainer1.point.y + symbolContainer1.symbol.getHeight() + dis);

			for (int y = x + 1; y < symbols.size(); y++) {
				if (y != x) {
					final SymbolContainer symbolContainer2 = symbols.get(y);
					final Rectangle rect2 = new Rectangle((int) symbolContainer2.point.x, (int) symbolContainer2.point.y,
							(int) symbolContainer2.point.x + symbolContainer2.symbol.getWidth(),
							(int) symbolContainer2.point.y + symbolContainer2.symbol.getHeight());

					if (rect2.intersects(rect1)) {
						symbols.remove(y);
						y--;
					}
				}
			}
		}
	}

	/**
	 * Removes the the symbols that overlap with area labels.
	 * 
	 * @param symbols
	 *            list of symbols
	 * @param pTC
	 *            list of labels
	 */
	private static void removeOverlappingSymbolsWithAreaLabels(List<SymbolContainer> symbols, List<PointTextContainer> pTC) {
		int dis = LABEL_DISTANCE_TO_SYMBOL;

		for (int x = 0; x < pTC.size(); x++) {
			final PointTextContainer label = pTC.get(x);
			final Rectangle rect1 = new Rectangle((int) label.x - dis, (int) (label.y - label.boundary.getHeight())
					- dis, (int) (label.x + label.boundary.getWidth() + dis), (int) (label.y + dis));

			for (int y = 0; y < symbols.size(); y++) {
				final SymbolContainer symbolContainer = symbols.get(y);
				final Rectangle rect2 = new Rectangle((int) symbolContainer.point.x, (int) symbolContainer.point.y,
						(int) (symbolContainer.point.x + symbolContainer.symbol.getWidth()),
						(int) (symbolContainer.point.y + symbolContainer.symbol.getHeight()));

				if (rect1.intersects(rect2)) {
					symbols.remove(y);
					y--;
				}
			}
		}
	}
}
