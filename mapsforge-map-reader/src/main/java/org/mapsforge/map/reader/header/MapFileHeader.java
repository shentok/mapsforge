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
package org.mapsforge.map.reader.header;

import java.io.IOException;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.reader.ReadBuffer;

/**
 * Reads and validates the header data from a binary map file.
 */
public class MapFileHeader {
	/**
	 * Maximum valid base zoom level of a sub-file.
	 */
	private static final int BASE_ZOOM_LEVEL_MAX = 20;

	/**
	 * Minimum size of the file header in bytes.
	 */
	private static final int HEADER_SIZE_MIN = 70;

	/**
	 * Length of the debug signature at the beginning of the index.
	 */
	private static final byte SIGNATURE_LENGTH_INDEX = 16;

	/**
	 * A single whitespace character.
	 */
	private static final char SPACE = ' ';

	private MapFileInfo mapFileInfo;
	private SubFileParameter[] subFileParameters;
	private byte zoomLevelMaximum;
	private byte zoomLevelMinimum;

	/**
	 * @return a MapFileInfo containing the header data.
	 */
	public MapFileInfo getMapFileInfo() {
		return this.mapFileInfo;
	}

	/**
	 * @param zoomLevel
	 *            the originally requested zoom level.
	 * @return the closest possible zoom level which is covered by a sub-file.
	 */
	public byte getQueryZoomLevel(byte zoomLevel) {
		if (zoomLevel > this.zoomLevelMaximum) {
			return this.zoomLevelMaximum;
		} else if (zoomLevel < this.zoomLevelMinimum) {
			return this.zoomLevelMinimum;
		}
		return zoomLevel;
	}

	/**
	 * @param queryZoomLevel
	 *            the zoom level for which the sub-file parameters are needed.
	 * @return the sub-file parameters for the given zoom level.
	 */
	public SubFileParameter getSubFileParameter(int queryZoomLevel) {
		return this.subFileParameters[queryZoomLevel];
	}

	/**
	 * Reads and validates the header block from the map file.
	 * 
	 * @param readBuffer
	 *            the ReadBuffer for the file data.
	 * @param fileSize
	 *            the size of the map file in bytes.
	 * @return a FileOpenResult containing an error message in case of a failure.
	 * @throws IOException
	 *             if an error occurs while reading the file.
	 */
	public static MapFileHeader readHeader(ReadBuffer readBuffer, long fileSize) throws IOException {
		RequiredFields.readMagicByte(readBuffer);
		RequiredFields.readRemainingHeader(readBuffer);

		MapFileInfoBuilder mapFileInfoBuilder = new MapFileInfoBuilder();

		mapFileInfoBuilder.fileVersion = RequiredFields.readFileVersion(readBuffer);
		mapFileInfoBuilder.fileSize = RequiredFields.readFileSize(readBuffer);
		if (mapFileInfoBuilder.fileSize != fileSize) {
			throw new IOException("invalid file size: " + mapFileInfoBuilder.fileSize);
		}
		mapFileInfoBuilder.mapDate = RequiredFields.readMapDate(readBuffer);
		mapFileInfoBuilder.boundingBox = RequiredFields.readBoundingBox(readBuffer);
		mapFileInfoBuilder.tilePixelSize = RequiredFields.readTilePixelSize(readBuffer);
		mapFileInfoBuilder.projectionName = RequiredFields.readProjectionName(readBuffer);

		mapFileInfoBuilder.optionalFields = OptionalFields.readOptionalFields(readBuffer);

		mapFileInfoBuilder.poiTags = RequiredFields.readPoiTags(readBuffer);
		mapFileInfoBuilder.wayTags = RequiredFields.readWayTags(readBuffer);
		// get and check the number of sub-files (1 byte)
		mapFileInfoBuilder.numberOfSubFiles = readBuffer.readByte();
		if (mapFileInfoBuilder.numberOfSubFiles < 1) {
			throw new IOException("invalid number of sub-files: " + mapFileInfoBuilder.numberOfSubFiles);
		}

		MapFileHeader result = new MapFileHeader();
		result.mapFileInfo = mapFileInfoBuilder.build();
		result.subFileParameters = readSubFileParameters(readBuffer, fileSize, mapFileInfoBuilder.numberOfSubFiles, mapFileInfoBuilder.boundingBox, mapFileInfoBuilder.optionalFields.isDebugFile);

		result.zoomLevelMinimum = Byte.MAX_VALUE;
		result.zoomLevelMaximum = Byte.MIN_VALUE;
		for (SubFileParameter subFileParameter : result.subFileParameters) {
			// update the global minimum and maximum zoom level information
			if (result.zoomLevelMinimum > subFileParameter.zoomLevelMin) {
				result.zoomLevelMinimum = subFileParameter.zoomLevelMin;
			}
			if (result.zoomLevelMaximum < subFileParameter.zoomLevelMax) {
				result.zoomLevelMaximum = subFileParameter.zoomLevelMax;
			}
		}
		
		return result;
	}

	private static SubFileParameter[] readSubFileParameters(ReadBuffer readBuffer, long fileSize,
			byte numberOfSubFiles, BoundingBox boundingBox, boolean isDebugFile) throws IOException {
		SubFileParameter[] tempSubFileParameters = new SubFileParameter[numberOfSubFiles];

		byte zoomLevelMaximum = Byte.MIN_VALUE;
		// get and check the information for each sub-file
		for (byte currentSubFile = 0; currentSubFile < numberOfSubFiles; ++currentSubFile) {
			SubFileParameterBuilder subFileParameterBuilder = new SubFileParameterBuilder();

			// get and check the base zoom level (1 byte)
			byte baseZoomLevel = readBuffer.readByte();
			if (baseZoomLevel < 0 || baseZoomLevel > BASE_ZOOM_LEVEL_MAX) {
				throw new IOException("invalid base zooom level: " + baseZoomLevel);
			}
			subFileParameterBuilder.baseZoomLevel = baseZoomLevel;

			// get and check the minimum zoom level (1 byte)
			byte zoomLevelMin = readBuffer.readByte();
			if (zoomLevelMin < 0 || zoomLevelMin > 22) {
				throw new IOException("invalid minimum zoom level: " + zoomLevelMin);
			}
			subFileParameterBuilder.zoomLevelMin = zoomLevelMin;

			// get and check the maximum zoom level (1 byte)
			byte zoomLevelMax = readBuffer.readByte();
			if (zoomLevelMax < 0 || zoomLevelMax > 22) {
				throw new IOException("invalid maximum zoom level: " + zoomLevelMax);
			}
			subFileParameterBuilder.zoomLevelMax = zoomLevelMax;

			// update the global minimum and maximum zoom level information
			if (zoomLevelMaximum < zoomLevelMax) {
				zoomLevelMaximum = zoomLevelMax;
			}

			// check for valid zoom level range
			if (zoomLevelMin > zoomLevelMax) {
				throw new IOException("invalid zoom level range: " + zoomLevelMin + SPACE + zoomLevelMax);
			}

			// get and check the start address of the sub-file (8 bytes)
			long startAddress = readBuffer.readLong();
			if (startAddress < HEADER_SIZE_MIN || startAddress >= fileSize) {
				throw new IOException("invalid start address: " + startAddress);
			}
			subFileParameterBuilder.startAddress = startAddress;

			long indexStartAddress = startAddress;
			if (isDebugFile) {
				// the sub-file has an index signature before the index
				indexStartAddress += SIGNATURE_LENGTH_INDEX;
			}
			subFileParameterBuilder.indexStartAddress = indexStartAddress;

			// get and check the size of the sub-file (8 bytes)
			long subFileSize = readBuffer.readLong();
			if (subFileSize < 1) {
				throw new IOException("invalid sub-file size: " + subFileSize);
			}
			subFileParameterBuilder.subFileSize = subFileSize;

			// add the current sub-file to the list of sub-files
			tempSubFileParameters[currentSubFile] = new SubFileParameter(subFileParameterBuilder, boundingBox);
		}

		// create and fill the lookup table for the sub-files
		SubFileParameter[] result = new SubFileParameter[zoomLevelMaximum + 1];
		for (SubFileParameter subFileParameter : tempSubFileParameters) {
			for (byte zoomLevel = subFileParameter.zoomLevelMin; zoomLevel <= subFileParameter.zoomLevelMax; ++zoomLevel) {
				result[zoomLevel] = subFileParameter;
			}
		}
		
		return result;
	}
}
