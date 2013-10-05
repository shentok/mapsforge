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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.model.Tile;

/**
 * This class process the methods for the Dependency Cache. It's connected with the LabelPlacement class. The main goal
 * is, to remove double labels and symbols that are already rendered, from the actual object. Labels and symbols that,
 * would be rendered on an already drawn Tile, will be deleted too.
 */
class DependencyCache {
	/**
	 * The class holds the data for a symbol with dependencies on other tiles.
	 * 
	 * @param <T>
	 *            only two types are reasonable. The DependencySymbol or DependencyText class.
	 */
	private static class Dependency<T> {
		final Point point;
		final T value;

		Dependency(T value, Point point) {
			this.value = value;
			this.point = point;
		}
	}

	/**
	 * This class holds all the information off the possible dependencies on a object.
	 */
	private static class DependencyOnTile {
		boolean drawn;
		List<Dependency<PointTextContainer>> labels;
		List<Dependency<Bitmap>> symbols;

		/**
		 * Initialize label, symbol and drawn.
		 */
		DependencyOnTile() {
			this.labels = null;
			this.symbols = null;
			this.drawn = false;
		}

		/**
		 * @param toAdd
		 *            a dependency Symbol
		 */
		void addSymbol(Dependency<Bitmap> toAdd) {
			if (this.symbols == null) {
				this.symbols = new ArrayList<Dependency<Bitmap>>();
			}
			this.symbols.add(toAdd);
		}

		/**
		 * @param toAdd
		 *            a Dependency Text
		 */
		void addText(Dependency<PointTextContainer> toAdd) {
			if (this.labels == null) {
				this.labels = new ArrayList<Dependency<PointTextContainer>>();
			}
			this.labels.add(toAdd);
		}
	}

	/**
	 * Hash table, that connects the Tiles with their entries in the dependency cache.
	 */
	final Map<Tile, DependencyOnTile> dependencyTable;

	private DependencyOnTile currentDependencyOnTile;
	private Tile currentTile;

	/**
	 * Constructor for this class, that creates a hashtable for the dependencies.
	 */
	DependencyCache() {
		this.dependencyTable = new Hashtable<Tile, DependencyOnTile>(60);
	}

	/**
	 * This method fills the entries in the dependency cache of the tiles, if their dependencies.
	 * 
	 * @param labels
	 *            current labels, that will be displayed.
	 * @param symbols
	 *            current symbols, that will be displayed.
	 * @param areaLabels
	 *            current areaLabels, that will be displayed.
	 */
	void fillDependencyOnTile(List<PointTextContainer> labels, List<SymbolContainer> symbols,
			List<PointTextContainer> areaLabels) {
		this.currentDependencyOnTile.drawn = true;

		fillDependencyLabels(labels);
		fillDependencyLabels(areaLabels);
		fillDependencySymbols(symbols);

		if (this.currentDependencyOnTile.labels != null) {
			addLabelsFromDependencyOnTile(labels);
		}
		if (this.currentDependencyOnTile.symbols != null) {
			addSymbolsFromDependencyOnTile(symbols);
		}
	}

	/**
	 * This method must be called, before the dependencies will be handled correctly. Because it sets the actual Tile
	 * and looks if it has already dependencies.
	 */
	void setCurrentTile(Tile tile) {
		this.currentTile = new Tile(tile.tileX, tile.tileY, tile.zoomLevel);

		if (this.dependencyTable.get(this.currentTile) == null) {
			this.dependencyTable.put(this.currentTile, new DependencyOnTile());
		}
		this.currentDependencyOnTile = this.dependencyTable.get(this.currentTile);

		Tile left = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile right = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile up = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile down = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		Tile leftup = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile leftdown = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
		Tile rightup = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile rightdown = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		if (this.dependencyTable.get(up) == null) {
			this.dependencyTable.put(up, new DependencyOnTile());
		}
		if (this.dependencyTable.get(down) == null) {
			this.dependencyTable.put(down, new DependencyOnTile());
		}
		if (this.dependencyTable.get(left) == null) {
			this.dependencyTable.put(left, new DependencyOnTile());
		}
		if (this.dependencyTable.get(right) == null) {
			this.dependencyTable.put(right, new DependencyOnTile());
		}
		if (this.dependencyTable.get(leftdown) == null) {
			this.dependencyTable.put(leftdown, new DependencyOnTile());
		}
		if (this.dependencyTable.get(rightup) == null) {
			this.dependencyTable.put(rightup, new DependencyOnTile());
		}
		if (this.dependencyTable.get(leftup) == null) {
			this.dependencyTable.put(leftup, new DependencyOnTile());
		}
		if (this.dependencyTable.get(rightdown) == null) {
			this.dependencyTable.put(rightdown, new DependencyOnTile());
		}
	}

	/**
	 * Removes the are labels from the actual list, that would be rendered in a Tile that has already be drawn.
	 * 
	 * @param areaLabels
	 *            current area Labels, that will be displayed
	 */
	void removeAreaLabelsInAlreadyDrawnAreas(List<PointTextContainer> areaLabels) {
		long maxTileNumber = Tile.getMaxTileNumber(this.currentTile.zoomLevel);

		boolean left = false;
		if (this.currentTile.tileX > 0) {
			final Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			left = this.dependencyTable.get(lefttmp).drawn;
		}

		boolean right = false;
		if (this.currentTile.tileX < maxTileNumber) {
			final Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			right = this.dependencyTable.get(righttmp).drawn;
		}

		boolean up = false;
		if (this.currentTile.tileY > 0) {
			final Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
			up = this.dependencyTable.get(uptmp).drawn;
		}

		boolean down = false;
		if (this.currentTile.tileY < maxTileNumber) {
			final Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
			down = this.dependencyTable.get(downtmp).drawn;
		}

		for (int i = 0; i < areaLabels.size(); i++) {
			final PointTextContainer label = areaLabels.get(i);

			if (up && label.y - label.boundary.getHeight() < 0.0f) {
				areaLabels.remove(i);
				i--;
				continue;
			}

			if (down && label.y > Tile.TILE_SIZE) {
				areaLabels.remove(i);
				i--;
				continue;
			}
			if (left && label.x < 0.0f) {
				areaLabels.remove(i);
				i--;
				continue;
			}
			if (right && label.x + label.boundary.getWidth() > Tile.TILE_SIZE) {
				areaLabels.remove(i);
				i--;
				continue;
			}
		}
	}

	/**
	 * Removes all objects that overlaps with the objects from the dependency cache.
	 * 
	 * @param labels
	 *            labels from the current object
	 * @param areaLabels
	 *            area labels from the current object
	 * @param symbols
	 *            symbols from the current object
	 */
	void removeOverlappingObjectsWithDependencyOnTile(List<PointTextContainer> labels,
			List<PointTextContainer> areaLabels, List<SymbolContainer> symbols) {
		if (this.currentDependencyOnTile.labels != null && !this.currentDependencyOnTile.labels.isEmpty()) {
			removeOverlappingLabelsWithDependencyLabels(labels);
			removeOverlappingSymbolsWithDependencyLabels(symbols);
			removeOverlappingAreaLabelsWithDependencyLabels(areaLabels);
		}

		if (this.currentDependencyOnTile.symbols != null && !this.currentDependencyOnTile.symbols.isEmpty()) {
			removeOverlappingSymbolsWithDepencySymbols(symbols, 2);
			removeOverlappingAreaLabelsWithDependencySymbols(areaLabels);
		}
	}

	/**
	 * When the LabelPlacement class generates potential label positions for an POI, there should be no possible
	 * positions, that collide with existing symbols or labels in the dependency Cache. This class implements this
	 * functionality.
	 * 
	 * @param refPos
	 *            possible label positions form the two or four point Greedy
	 */
	void removeOutOfTileReferencePoints(LabelPlacement.ReferencePosition[] refPos) {
		final long maxTileNumber = Tile.getMaxTileNumber(this.currentTile.zoomLevel);

		boolean left = false;
		if (this.currentTile.tileX > 0) {
			final Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			left = this.dependencyTable.get(lefttmp).drawn;
		}

		boolean right = false;
		if (this.currentTile.tileX < maxTileNumber) {
			final Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			right = this.dependencyTable.get(righttmp).drawn;
		}

		boolean up = false;
		if (this.currentTile.tileY > 0) {
			final Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
			up = this.dependencyTable.get(uptmp).drawn;
		}

		boolean down = false;
		if (this.currentTile.tileY < maxTileNumber) {
			final Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
			down = this.dependencyTable.get(downtmp).drawn;
		}

		for (int i = 0; i < refPos.length; i++) {
			final LabelPlacement.ReferencePosition ref = refPos[i];

			if (ref == null) {
				continue;
			}

			if (up && ref.y - ref.height < 0) {
				refPos[i] = null;
				continue;
			}

			if (down && ref.y >= Tile.TILE_SIZE) {
				refPos[i] = null;
				continue;
			}

			if (left && ref.x < 0) {
				refPos[i] = null;
				continue;
			}

			if (right && ref.x + ref.width > Tile.TILE_SIZE) {
				refPos[i] = null;
			}
		}
	}

	/**
	 * @brief Removes all Reference Points that intersects with Labels from the Dependency Cache 
	 * @param refPos
	 */
	void removeOverlappingLabels(LabelPlacement.ReferencePosition[] refPos) {
		if (this.currentDependencyOnTile == null) {
			throw new IllegalArgumentException();
		}

		int dis = 2;
			if (this.currentDependencyOnTile.labels != null) {
				for (Dependency<PointTextContainer> depLabel : currentDependencyOnTile.labels) {
					final Rectangle rect1 = new Rectangle((int) depLabel.point.x - dis,
							(int) (depLabel.point.y - depLabel.value.boundary.getHeight()) - dis,
							(int) (depLabel.point.x + depLabel.value.boundary.getWidth() + dis),
							(int) (depLabel.point.y + dis));

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
	}

	void removeOverlappingSymbols(LabelPlacement.ReferencePosition[] refPos) {
		if (this.currentDependencyOnTile == null) {
			throw new IllegalArgumentException();
		}

			if (this.currentDependencyOnTile.symbols != null) {
				for (Dependency<Bitmap> symbols2 : this.currentDependencyOnTile.symbols) {
					final Rectangle rect1 = new Rectangle((int) symbols2.point.x, (int) (symbols2.point.y),
							(int) (symbols2.point.x + symbols2.value.getWidth()),
							(int) (symbols2.point.y + symbols2.value.getHeight()));

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
	}

	void removeSymbolsFromDrawnAreas(List<SymbolContainer> symbols) {
		final long maxTileNumber = Tile.getMaxTileNumber(this.currentTile.zoomLevel);

		boolean left = false;
		if (this.currentTile.tileX > 0) {
			final Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			left = this.dependencyTable.get(lefttmp).drawn;
		}

		boolean right = false;
		if (this.currentTile.tileX < maxTileNumber) {
			final Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
			right = this.dependencyTable.get(righttmp).drawn;
		}

		boolean up = false;
		if (this.currentTile.tileY > 0) {
			final Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
			up = this.dependencyTable.get(uptmp).drawn;
		}

		boolean down = false;
		if (this.currentTile.tileY < maxTileNumber) {
			final Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
			down = this.dependencyTable.get(downtmp).drawn;
		}

		for (int i = 0; i < symbols.size(); i++) {
			final SymbolContainer ref = symbols.get(i);

			if (up && ref.point.y < 0) {
				symbols.remove(i);
				i--;
				continue;
			}

			if (down && ref.point.y + ref.symbol.getHeight() > Tile.TILE_SIZE) {
				symbols.remove(i);
				i--;
				continue;
			}
			if (left && ref.point.x < 0) {
				symbols.remove(i);
				i--;
				continue;
			}
			if (right && ref.point.x + ref.symbol.getWidth() > Tile.TILE_SIZE) {
				symbols.remove(i);
				i--;
				continue;
			}
		}
	}

	private void addLabelsFromDependencyOnTile(List<PointTextContainer> labels) {
		for (Dependency<PointTextContainer> depLabel : this.currentDependencyOnTile.labels) {
			if (depLabel.value.paintBack != null) {
				labels.add(new PointTextContainer(depLabel.value.text, depLabel.point.x,
						depLabel.point.y, depLabel.value.paintFront, depLabel.value.paintBack));
			} else {
				labels.add(new PointTextContainer(depLabel.value.text, depLabel.point.x,
						depLabel.point.y, depLabel.value.paintFront));
			}
		}
	}

	private void addSymbolsFromDependencyOnTile(List<SymbolContainer> symbols) {
		for (Dependency<Bitmap> depSmb : this.currentDependencyOnTile.symbols) {
			symbols.add(new SymbolContainer(depSmb.value, depSmb.point));
		}
	}

	/**
	 * Fills the dependency entry from the object and the neighbor tiles with the dependency information, that are
	 * necessary for drawing. To do that every label and symbol that will be drawn, will be checked if it produces
	 * dependencies with other tiles.
	 * 
	 * @param pTC
	 *            list of the labels
	 */
	private void fillDependencyLabels(List<PointTextContainer> pTC) {
		Tile left = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile right = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile up = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile down = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		Tile leftup = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile leftdown = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
		Tile rightup = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile rightdown = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		for (PointTextContainer label : pTC) {
			boolean alreadyAdded = false;

			// up
			if ((label.y - label.boundary.getHeight() < 0.0f) && (!this.dependencyTable.get(up).drawn)) {
				alreadyAdded = true;

				this.currentDependencyOnTile
						.addText(new Dependency<PointTextContainer>(label, new Point(label.x, label.y)));

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(up);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x, label.y + Tile.TILE_SIZE)));
				}

				if ((label.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(leftup);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x + Tile.TILE_SIZE, label.y
							+ Tile.TILE_SIZE)));
				}

				if ((label.x + label.boundary.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightup).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(rightup);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x - Tile.TILE_SIZE, label.y
							+ Tile.TILE_SIZE)));
				}
			}

			// down
			if ((label.y > Tile.TILE_SIZE) && (!this.dependencyTable.get(down).drawn)) {
				if (alreadyAdded == false) {
					alreadyAdded = true;

					this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
							label.y)));
				}

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(down);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x, label.y - Tile.TILE_SIZE)));
				}

				if ((label.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(leftdown);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x + Tile.TILE_SIZE, label.y
							- Tile.TILE_SIZE)));
				}

				if ((label.x + label.boundary.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightdown).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(rightdown);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x - Tile.TILE_SIZE, label.y
							- Tile.TILE_SIZE)));
				}
			}
			// left

			if ((label.x < 0.0f) && (!this.dependencyTable.get(left).drawn)) {
				if (alreadyAdded == false) {
					alreadyAdded = true;

					this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
							label.y)));
				}

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(left);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x + Tile.TILE_SIZE, label.y)));
				}
			}
			// right
			if ((label.x + label.boundary.getWidth() > Tile.TILE_SIZE) && (!this.dependencyTable.get(right).drawn)) {
				if (alreadyAdded == false) {
					alreadyAdded = true;

					this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
							label.y)));
				}

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(right);
					linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x - Tile.TILE_SIZE, label.y)));
				}
			}

			// check symbols

			if ((label.symbol != null) && (alreadyAdded == false)) {
				if ((label.symbol.point.y <= 0.0f) && (!this.dependencyTable.get(up).drawn)) {
					alreadyAdded = true;
					this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
							label.y)));

					{
						final DependencyOnTile linkedDep = this.dependencyTable.get(up);
						linkedDep.addText(new Dependency<PointTextContainer>(label,
								new Point(label.x, label.y + Tile.TILE_SIZE)));
					}

					if ((label.symbol.point.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
						final DependencyOnTile linkedDep = this.dependencyTable.get(leftup);
						linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x + Tile.TILE_SIZE,
								label.y + Tile.TILE_SIZE)));
					}

					if ((label.symbol.point.x + label.symbol.symbol.getWidth() > Tile.TILE_SIZE)
							&& (!this.dependencyTable.get(rightup).drawn)) {
						final DependencyOnTile linkedDep = this.dependencyTable.get(rightup);
						linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x - Tile.TILE_SIZE,
								label.y + Tile.TILE_SIZE)));
					}
				}

				if ((label.symbol.point.y + label.symbol.symbol.getHeight() >= Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(down).drawn)) {
					if (alreadyAdded == false) {
						alreadyAdded = true;

						this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
								label.y)));
					}

					{
						final DependencyOnTile linkedDep = this.dependencyTable.get(down);
						linkedDep.addText(new Dependency<PointTextContainer>(label,
								new Point(label.x, label.y + Tile.TILE_SIZE)));
					}

					if ((label.symbol.point.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
						final DependencyOnTile linkedDep = this.dependencyTable.get(leftdown);
						linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x + Tile.TILE_SIZE,
								label.y - Tile.TILE_SIZE)));
					}

					if ((label.symbol.point.x + label.symbol.symbol.getWidth() > Tile.TILE_SIZE)
							&& (!this.dependencyTable.get(rightdown).drawn)) {
						final DependencyOnTile linkedDep = this.dependencyTable.get(rightdown);
						linkedDep.addText(new Dependency<PointTextContainer>(label, new Point(label.x - Tile.TILE_SIZE,
								label.y - Tile.TILE_SIZE)));
					}
				}

				if ((label.symbol.point.x <= 0.0f) && (!this.dependencyTable.get(left).drawn)) {
					if (alreadyAdded == false) {
						alreadyAdded = true;

						this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
								label.y)));
					}

					final DependencyOnTile linkedDep = this.dependencyTable.get(left);
					linkedDep.addText(new Dependency<PointTextContainer>(label,
							new Point(label.x - Tile.TILE_SIZE, label.y)));
				}

				if ((label.symbol.point.x + label.symbol.symbol.getWidth() >= Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(right).drawn)) {
					if (alreadyAdded == false) {
						alreadyAdded = true;

						this.currentDependencyOnTile.addText(new Dependency<PointTextContainer>(label, new Point(label.x,
								label.y)));
					}

					final DependencyOnTile linkedDep = this.dependencyTable.get(right);
					linkedDep.addText(new Dependency<PointTextContainer>(label,
							new Point(label.x + Tile.TILE_SIZE, label.y)));
				}
			}
		}
	}
	
	private void fillDependencySymbols(List<SymbolContainer> symbols) {
		Tile left = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile right = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile up = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile down = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		Tile leftup = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile leftdown = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
		Tile rightup = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile rightdown = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		for (SymbolContainer container : symbols) {
			boolean addSmb = false;

			// up
			if ((container.point.y < 0.0f) && (!this.dependencyTable.get(up).drawn)) {
				if (addSmb == false) {
					addSmb = true;
					this.currentDependencyOnTile.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(
							container.point.x, container.point.y)));
				}

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(up);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x, container.point.y
							+ Tile.TILE_SIZE)));
				}

				if ((container.point.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(leftup);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x
							+ Tile.TILE_SIZE, container.point.y + Tile.TILE_SIZE)));
				}

				if ((container.point.x + container.symbol.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightup).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(rightup);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x
							- Tile.TILE_SIZE, container.point.y + Tile.TILE_SIZE)));
				}
			}

			// down
			if ((container.point.y + container.symbol.getHeight() > Tile.TILE_SIZE)
					&& (!this.dependencyTable.get(down).drawn)) {
				if (addSmb == false) {
					addSmb = true;
					this.currentDependencyOnTile.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(
							container.point.x, container.point.y)));
				}

				{
					final DependencyOnTile linkedDep = this.dependencyTable.get(down);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x, container.point.y
							- Tile.TILE_SIZE)));
				}

				if ((container.point.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(leftdown);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x
							+ Tile.TILE_SIZE, container.point.y - Tile.TILE_SIZE)));
				}

				if ((container.point.x + container.symbol.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightdown).drawn)) {
					final DependencyOnTile linkedDep = this.dependencyTable.get(rightdown);
					linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x
							- Tile.TILE_SIZE, container.point.y - Tile.TILE_SIZE)));
				}
			}

			// left
			if ((container.point.x < 0.0f) && (!this.dependencyTable.get(left).drawn)) {
				if (addSmb == false) {
					addSmb = true;
					this.currentDependencyOnTile.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(
							container.point.x, container.point.y)));
				}

				final DependencyOnTile linkedDep = this.dependencyTable.get(left);
				linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x + Tile.TILE_SIZE,
						container.point.y)));
			}

			// right
			if ((container.point.x + container.symbol.getWidth() > Tile.TILE_SIZE)
					&& (!this.dependencyTable.get(right).drawn)) {
				if (addSmb == false) {
					addSmb = true;
					this.currentDependencyOnTile.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(
							container.point.x, container.point.y)));
				}

				final DependencyOnTile linkedDep = this.dependencyTable.get(right);
				linkedDep.addSymbol(new Dependency<Bitmap>(container.symbol, new Point(container.point.x - Tile.TILE_SIZE,
						container.point.y)));
			}
		}
	}

	private void removeOverlappingAreaLabelsWithDependencyLabels(List<PointTextContainer> areaLabels) {
		for (Dependency<PointTextContainer> depLabel : this.currentDependencyOnTile.labels) {
			final Rectangle rect1 = new Rectangle((int) (depLabel.point.x),
					(int) (depLabel.point.y - depLabel.value.boundary.getHeight()),
					(int) (depLabel.point.x + depLabel.value.boundary.getWidth()),
					(int) (depLabel.point.y));

			for (int x = 0; x < areaLabels.size(); x++) {
				final PointTextContainer pTC = areaLabels.get(x);

				final Rectangle rect2 = new Rectangle((int) pTC.x, (int) pTC.y - pTC.boundary.getHeight(), (int) pTC.x
						+ pTC.boundary.getWidth(), (int) pTC.y);

				if (rect2.intersects(rect1)) {
					areaLabels.remove(x);
					x--;
				}
			}
		}
	}

	private void removeOverlappingAreaLabelsWithDependencySymbols(List<PointTextContainer> areaLabels) {
		for (Dependency<Bitmap> depSmb : this.currentDependencyOnTile.symbols) {
			final Rectangle rect1 = new Rectangle((int) depSmb.point.x, (int) depSmb.point.y, (int) depSmb.point.x
					+ depSmb.value.getWidth(), (int) depSmb.point.y + depSmb.value.getHeight());

			for (int x = 0; x < areaLabels.size(); x++) {
				final PointTextContainer label = areaLabels.get(x);

				final Rectangle rect2 = new Rectangle((int) (label.x), (int) (label.y - label.boundary.getHeight()),
						(int) (label.x + label.boundary.getWidth()), (int) (label.y));

				if (rect2.intersects(rect1)) {
					areaLabels.remove(x);
					x--;
				}
			}
		}
	}

	private void removeOverlappingLabelsWithDependencyLabels(List<PointTextContainer> labels) {
		for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
			for (int x = 0; x < labels.size(); x++) {
				if ((labels.get(x).text.equals(this.currentDependencyOnTile.labels.get(i).value.text))
						&& (labels.get(x).paintFront
								.equals(this.currentDependencyOnTile.labels.get(i).value.paintFront))
						&& (labels.get(x).paintBack.equals(this.currentDependencyOnTile.labels.get(i).value.paintBack))) {
					labels.remove(x);
					i--;
					break;
				}
			}
		}
	}

	private void removeOverlappingSymbolsWithDepencySymbols(List<SymbolContainer> symbols, int dis) {
		for (Dependency<Bitmap> sym2 : this.currentDependencyOnTile.symbols) {
			final Rectangle rect1 = new Rectangle((int) sym2.point.x - dis, (int) sym2.point.y - dis, (int) sym2.point.x
					+ sym2.value.getWidth() + dis, (int) sym2.point.y + sym2.value.getHeight() + dis);

			for (int y = 0; y < symbols.size(); y++) {
				final SymbolContainer symbolContainer = symbols.get(y);
				final Rectangle rect2 = new Rectangle((int) symbolContainer.point.x, (int) symbolContainer.point.y,
						(int) symbolContainer.point.x + symbolContainer.symbol.getWidth(),
						(int) symbolContainer.point.y + symbolContainer.symbol.getHeight());

				if (rect2.intersects(rect1)) {
					symbols.remove(y);
					y--;
				}
			}
		}
	}

	private void removeOverlappingSymbolsWithDependencyLabels(List<SymbolContainer> symbols) {
		for (Dependency<PointTextContainer> depLabel : this.currentDependencyOnTile.labels) {
			final Rectangle rect1 = new Rectangle((int) (depLabel.point.x),
					(int) (depLabel.point.y - depLabel.value.boundary.getHeight()),
					(int) (depLabel.point.x + depLabel.value.boundary.getWidth()),
					(int) (depLabel.point.y));

			for (int x = 0; x < symbols.size(); x++) {
				final SymbolContainer smb = symbols.get(x);

				final Rectangle rect2 = new Rectangle((int) smb.point.x, (int) smb.point.y, (int) smb.point.x
						+ smb.symbol.getWidth(), (int) smb.point.y + smb.symbol.getHeight());

				if (rect2.intersects(rect1)) {
					symbols.remove(x);
					x--;
				}
			}
		}
	}
}
