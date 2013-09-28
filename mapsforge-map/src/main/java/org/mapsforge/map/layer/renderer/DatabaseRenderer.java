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
import java.util.Arrays;
import java.util.List;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.MapReadResult;
import org.mapsforge.map.reader.PointOfInterest;
import org.mapsforge.map.reader.Way;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.rule.RenderTheme;

/**
 * A DatabaseRenderer renders map tiles by reading from a {@link MapDatabase}.
 */
public class DatabaseRenderer implements RenderCallback {
	private static final Byte DEFAULT_START_ZOOM_LEVEL = Byte.valueOf((byte) 12);
	private static final byte LAYERS = 11;
	private static final double STROKE_INCREASE = 1.5;
	private static final byte STROKE_MIN_ZOOM_LEVEL = 12;
	private static final Tag TAG_NATURAL_WATER = new Tag("natural", "water");
	private static final Point[][] WATER_TILE_COORDINATES = getTilePixelCoordinates();
	private static final byte ZOOM_MAX = 22;

	private static Point[][] getTilePixelCoordinates() {
		Point point1 = new Point(0, 0);
		Point point2 = new Point(Tile.TILE_SIZE, 0);
		Point point3 = new Point(Tile.TILE_SIZE, Tile.TILE_SIZE);
		Point point4 = new Point(0, Tile.TILE_SIZE);
		return new Point[][] { { point1, point2, point3, point4, point1 } };
	}

	private static byte getValidLayer(byte layer) {
		if (layer < 0) {
			return 0;
		} else if (layer >= LAYERS) {
			return LAYERS - 1;
		} else {
			return layer;
		}
	}

	private final List<PointTextContainer> areaLabels;
	private final CanvasRasterer canvasRasterer;
	private Point[][] coordinates;
	private List<List<ShapePaintContainer>> drawingLayers;
	private final GraphicFactory graphicFactory;
	private final LabelPlacement labelPlacement;
	private final MapDatabase mapDatabase;
	private List<PointTextContainer> nodes;
	private final List<SymbolContainer> pointSymbols;
	private Point poiPosition;
	private RenderTheme previousJobTheme;
	private float previousTextScale;
	private byte previousZoomLevel;
	private ShapeContainer shapeContainer;
	private final List<WayTextContainer> wayNames;
	private final List<List<List<ShapePaintContainer>>> ways;
	private final List<SymbolContainer> waySymbols;

	/**
	 * Constructs a new DatabaseRenderer.
	 * 
	 * @param mapDatabase
	 *            the MapDatabase from which the map data will be read.
	 */
	public DatabaseRenderer(MapDatabase mapDatabase, GraphicFactory graphicFactory) {
		this.mapDatabase = mapDatabase;
		this.graphicFactory = graphicFactory;

		this.canvasRasterer = new CanvasRasterer(graphicFactory);
		this.labelPlacement = new LabelPlacement();

		this.ways = new ArrayList<List<List<ShapePaintContainer>>>(LAYERS);
		this.wayNames = new ArrayList<WayTextContainer>(64);
		this.nodes = new ArrayList<PointTextContainer>(64);
		this.areaLabels = new ArrayList<PointTextContainer>(64);
		this.waySymbols = new ArrayList<SymbolContainer>(64);
		this.pointSymbols = new ArrayList<SymbolContainer>(64);
	}

	/**
	 * Called when a job needs to be executed.
	 * 
	 * @param rendererJob
	 *            the job that should be executed.
	 */
	public Bitmap executeJob(RendererJob rendererJob) {
		RenderTheme jobTheme = rendererJob.renderTheme;
		if (!jobTheme.equals(this.previousJobTheme)) {
			final int levels = jobTheme.getLevels();
			this.ways.clear();

			for (byte i = LAYERS - 1; i >= 0; --i) {
				List<List<ShapePaintContainer>> innerWayList = new ArrayList<List<ShapePaintContainer>>(levels);
				for (int j = levels - 1; j >= 0; --j) {
					innerWayList.add(new ArrayList<ShapePaintContainer>(0));
				}
				this.ways.add(innerWayList);
			}

			this.previousJobTheme = jobTheme;
			this.previousZoomLevel = Byte.MIN_VALUE;
		}

		final byte zoomLevel = rendererJob.tile.zoomLevel;
		if (zoomLevel != this.previousZoomLevel) {
			final int zoomLevelDiff = Math.max(zoomLevel - STROKE_MIN_ZOOM_LEVEL, 0);
			final float strokeWidth = (float) Math.pow(STROKE_INCREASE, zoomLevelDiff);
			jobTheme.scaleStrokeWidth(strokeWidth);
			this.previousZoomLevel = zoomLevel;
		}

		final float textScale = rendererJob.textScale;
		if (Float.compare(textScale, this.previousTextScale) != 0) {
			jobTheme.scaleTextSize(textScale);
			this.previousTextScale = textScale;
		}

		if (this.mapDatabase != null) {
			MapReadResult mapReadResult = this.mapDatabase.readMapData(rendererJob.tile);
			if (mapReadResult != null) {
				for (PointOfInterest pointOfInterest : mapReadResult.pointOfInterests) {
					this.drawingLayers = this.ways.get(getValidLayer(pointOfInterest.layer));
					this.poiPosition = scaleLatLong(pointOfInterest.position, rendererJob.tile);
					jobTheme.matchNode(this, pointOfInterest.tags, rendererJob.tile.zoomLevel);
				}
	
				for (Way way : mapReadResult.ways) {
					this.drawingLayers = this.ways.get(getValidLayer(way.layer));
					// TODO what about the label position?
	
					LatLong[][] latLongs = way.latLongs;
					this.coordinates = new Point[latLongs.length][];
					for (int i = 0; i < this.coordinates.length; ++i) {
						this.coordinates[i] = new Point[latLongs[i].length];
	
						for (int j = 0; j < this.coordinates[i].length; ++j) {
							this.coordinates[i][j] = scaleLatLong(latLongs[i][j], rendererJob.tile);
						}
					}
					this.shapeContainer = new PolylineContainer(this.coordinates);
	
					if (GeometryUtils.isClosedWay(this.coordinates[0])) {
						jobTheme.matchClosedWay(this, way.tags, rendererJob.tile.zoomLevel);
					} else {
						jobTheme.matchLinearWay(this, way.tags, rendererJob.tile.zoomLevel);
					}
				}
	
				if (mapReadResult.isWater) {
					this.drawingLayers = this.ways.get(0);
					this.coordinates = WATER_TILE_COORDINATES;
					this.shapeContainer = new PolylineContainer(this.coordinates);
					jobTheme.matchClosedWay(this, Arrays.asList(TAG_NATURAL_WATER), rendererJob.tile.zoomLevel);
				}
			}
		}

		this.nodes = this.labelPlacement.placeLabels(this.nodes, this.pointSymbols, this.areaLabels, rendererJob.tile);

		Bitmap bitmap = this.graphicFactory.createBitmap(Tile.TILE_SIZE, Tile.TILE_SIZE);
		this.canvasRasterer.setCanvasBitmap(bitmap);
		this.canvasRasterer.fill(jobTheme.getMapBackground());
		this.canvasRasterer.drawWays(this.ways);
		this.canvasRasterer.drawSymbols(this.waySymbols);
		this.canvasRasterer.drawSymbols(this.pointSymbols);
		this.canvasRasterer.drawWayNames(this.wayNames);
		this.canvasRasterer.drawNodes(this.nodes);
		this.canvasRasterer.drawNodes(this.areaLabels);

		clearLists();

		return bitmap;
	}

	/**
	 * @return the start point (may be null).
	 */
	public LatLong getStartPoint() {
		if (this.mapDatabase != null && this.mapDatabase.hasOpenFile()) {
			MapFileInfo mapFileInfo = this.mapDatabase.getMapFileInfo();
			if (mapFileInfo.startPosition != null) {
				return mapFileInfo.startPosition;
			}
			return mapFileInfo.boundingBox.getCenterPoint();
		}

		return null;
	}

	/**
	 * @return the start zoom level (may be null).
	 */
	public Byte getStartZoomLevel() {
		if (this.mapDatabase != null && this.mapDatabase.hasOpenFile()) {
			MapFileInfo mapFileInfo = this.mapDatabase.getMapFileInfo();
			if (mapFileInfo.startZoomLevel != null) {
				return mapFileInfo.startZoomLevel;
			}
		}

		return DEFAULT_START_ZOOM_LEVEL;
	}

	/**
	 * @return the maximum zoom level.
	 */
	public byte getZoomLevelMax() {
		return ZOOM_MAX;
	}

	@Override
	public void renderArea(Paint fill, Paint stroke, int level) {
		List<ShapePaintContainer> list = this.drawingLayers.get(level);
		list.add(new ShapePaintContainer(this.shapeContainer, fill));
		list.add(new ShapePaintContainer(this.shapeContainer, stroke));
	}

	@Override
	public void renderAreaCaption(String caption, float verticalOffset, Paint fill, Paint stroke) {
		Point centerPosition = GeometryUtils.calculateCenterOfBoundingBox(this.coordinates[0]);
		this.areaLabels.add(new PointTextContainer(caption, centerPosition.x, centerPosition.y, fill, stroke));
	}

	@Override
	public void renderAreaSymbol(Bitmap symbol) {
		Point centerPosition = GeometryUtils.calculateCenterOfBoundingBox(this.coordinates[0]);
		int halfSymbolWidth = symbol.getWidth() / 2;
		int halfSymbolHeight = symbol.getHeight() / 2;
		double pointX = centerPosition.x - halfSymbolWidth;
		double pointY = centerPosition.y - halfSymbolHeight;
		Point shiftedCenterPosition = new Point(pointX, pointY);
		this.pointSymbols.add(new SymbolContainer(symbol, shiftedCenterPosition));
	}

	@Override
	public void renderPointOfInterestCaption(String caption, float verticalOffset, Paint fill, Paint stroke) {
		this.nodes.add(new PointTextContainer(caption, this.poiPosition.x, this.poiPosition.y + verticalOffset, fill,
				stroke));
	}

	@Override
	public void renderPointOfInterestCircle(float radius, Paint fill, Paint stroke, int level) {
		List<ShapePaintContainer> list = this.drawingLayers.get(level);
		list.add(new ShapePaintContainer(new CircleContainer(this.poiPosition, radius), fill));
		list.add(new ShapePaintContainer(new CircleContainer(this.poiPosition, radius), stroke));
	}

	@Override
	public void renderPointOfInterestSymbol(Bitmap symbol) {
		int halfSymbolWidth = symbol.getWidth() / 2;
		int halfSymbolHeight = symbol.getHeight() / 2;
		double pointX = this.poiPosition.x - halfSymbolWidth;
		double pointY = this.poiPosition.y - halfSymbolHeight;
		Point shiftedCenterPosition = new Point(pointX, pointY);
		this.pointSymbols.add(new SymbolContainer(symbol, shiftedCenterPosition));
	}

	@Override
	public void renderWay(Paint stroke, int level) {
		this.drawingLayers.get(level).add(new ShapePaintContainer(this.shapeContainer, stroke));
	}

	@Override
	public void renderWaySymbol(Bitmap symbolBitmap, boolean alignCenter, boolean repeatSymbol) {
		WayDecorator.renderSymbol(symbolBitmap, alignCenter, repeatSymbol, this.coordinates, this.waySymbols);
	}

	@Override
	public void renderWayText(String textKey, Paint fill, Paint stroke) {
		WayDecorator.renderText(textKey, fill, stroke, this.coordinates, this.wayNames);
	}

	private void clearLists() {
		for (int i = this.ways.size() - 1; i >= 0; --i) {
			List<List<ShapePaintContainer>> innerWayList = this.ways.get(i);
			for (int j = innerWayList.size() - 1; j >= 0; --j) {
				innerWayList.get(j).clear();
			}
		}

		this.areaLabels.clear();
		this.nodes.clear();
		this.pointSymbols.clear();
		this.wayNames.clear();
		this.waySymbols.clear();
	}

	/**
	 * Converts the given LatLong into XY coordinates on the current object.
	 * 
	 * @param latLong
	 *            the LatLong to convert.
	 * @return the XY coordinates on the current object.
	 */
	private static Point scaleLatLong(LatLong latLong, Tile tile) {
		double pixelX = MercatorProjection.longitudeToPixelX(latLong.longitude, tile.zoomLevel)
				- MercatorProjection.tileToPixel(tile.tileX);
		double pixelY = MercatorProjection.latitudeToPixelY(latLong.latitude, tile.zoomLevel)
				- MercatorProjection.tileToPixel(tile.tileY);

		return new Point((float) pixelX, (float) pixelY);
	}
}
