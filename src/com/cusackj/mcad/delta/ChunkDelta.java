package com.cusackj.mcad.delta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jnbt.ByteArrayTag;
import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.IntArrayTag;
import org.jnbt.IntTag;
import org.jnbt.ListTag;
import org.jnbt.LongTag;
import org.jnbt.NBTOutputStream;
import org.jnbt.NBTUtils;
import org.jnbt.Tag;

import com.cusackj.mcad.model.Chunk;

public class ChunkDelta {

	private class HeightMapDelta {
		private byte[] changeArray = new byte[128];
		private int[] changeValues;
	}

	// 3 bytes
	private ByteTag mLightPopulated;
	private ByteTag mTerrainPopulated;

	private ByteTag mV;
	// 8 bytes
	private IntTag mXPos;

	private IntTag mZPos;
	// 16 bytes
	private LongTag mInhabitedTime;

	private LongTag mLastUpdate;
	// VARIABLE * 2 bytes
	private ListTag mEntities;
	private ListTag mTileEntities;
	private ListTag mTileTicks;

	private byte[] mEntitiesBytes;
	private byte[] mTileEntitiesBytes;
	private byte[] mTileTicksBytes;

	// 256 bytes
	private ByteArrayTag mBiomes;
	// Not used in delta file
	private IntArrayTag mHeightMap;

	// 128 bytes + VARIABLE
	private HeightMapDelta mHeightMapDelta = new HeightMapDelta();

	// VARIABLE
	private ArrayList<SectionDelta> mSectionDeltas = new ArrayList<>();

	private boolean isEmpty = false;

	/** Represents an empty chunk, i.e. a chunk to be deleted by the delta analyser.
	 * 
	 * @param chunkBytes */
	public ChunkDelta() {
		isEmpty = true;
	}

	/** Constructs a delta from no source, i.e. contains information for an entire chunk.
	 * 
	 * @param source */
	public ChunkDelta(Chunk dest) {
		CompoundTag tDest = (CompoundTag) dest.getTag().getValue().get("Level");
		setDestinationTags(dest);

		mHeightMapDelta = new HeightMapDelta();
		for (int i = 0; i < mHeightMapDelta.changeArray.length; i++) {
			mHeightMapDelta.changeArray[i] = (byte) 0xFF;
		}
		mHeightMapDelta.changeValues = new int[256];
		for (int i = 0; i < mHeightMapDelta.changeValues.length; i++) {
			mHeightMapDelta.changeValues[i] = mHeightMap.getValue()[i];
		}
		List<Tag> destSections = ((ListTag) tDest.getValue().get("Sections")).getValue();
		for (Tag destTag : destSections) {
			CompoundTag destSection = (CompoundTag) destTag;
			mSectionDeltas.add(new SectionDelta(destSection));
		}
		// printSectionDeltaReport(mSectionDeltas.size(), 0, mSectionDeltas.size(), 0);

		try {
			mEntitiesBytes = tagToBytes(mEntities);
			mTileEntitiesBytes = tagToBytes(mTileEntities);
			if (mTileTicks != null) {
				mTileTicksBytes = tagToBytes(mTileTicks);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// VERIFY DELTA INTEGRITY
		CompoundTag sourceTag = NBTUtils.createEmptyChunk(getX(), getZ());

		byte[] sourceBytes = NBTUtils.writeTagToBytes(sourceTag);
		byte[] destBytes = NBTUtils.writeTagToBytes(dest.getTag());

		CompoundTag sourceMutate = (CompoundTag) NBTUtils.bytesToTag(sourceBytes);
		Chunk sourceChunkMutate = new Chunk(sourceMutate, getX(), getZ(), dest.getLastModified());
		applyDelta(sourceChunkMutate);

		byte[] sourceMutateBytes = NBTUtils.writeTagToBytes(sourceChunkMutate.getTag());
		System.out.println("$$$$$$$$$");
		if (destBytes.length != sourceMutateBytes.length) {
			System.out.println("Chunk delta is invalid in length" + " " + getX() + ", " + getZ());
		} else {
			System.out.println("Chunk delta is valid in length");
		}

		if (Arrays.equals(destBytes, sourceMutateBytes)) {
			System.out.println("Chunk delta is identical");
		} else {
			System.out.println("Chunk delta bytes are different! :(");
		}
		System.out.println("$$$$$$$$$");
	}

	/** Represents the difference between 2 chunks
	 * 
	 * @param chunk1
	 *            The source chunk
	 * @param chunk2
	 *            The destination chunk */
	public ChunkDelta(Chunk source, Chunk dest) {
		CompoundTag tSource = (CompoundTag) source.getTag().getValue().get("Level");
		CompoundTag tDest = (CompoundTag) dest.getTag().getValue().get("Level");

		setDestinationTags(dest);

		// Remove unchanged heightmap, section and block data
		IntArrayTag sourceHeightMap = (IntArrayTag) tSource.getValue().get("HeightMap");
		int[] sourceHeights = sourceHeightMap.getValue();
		int[] destHeights = mHeightMap.getValue();

		// Find changed sections, then create a new list with the delta-ed tags

		List<Tag> destSections = ((ListTag) tDest.getValue().get("Sections")).getValue();
		List<Tag> sourceSections = ((ListTag) tSource.getValue().get("Sections")).getValue();

		mHeightMapDelta = calculateHeightMapDelta(sourceHeights, destHeights);
		mSectionDeltas = calculateSectionDeltas(sourceSections, destSections);

		try {
			mEntitiesBytes = tagToBytes(mEntities);
			mTileEntitiesBytes = tagToBytes(mTileEntities);
			if (mTileTicks != null) {
				mTileTicksBytes = tagToBytes(mTileTicks);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		byte[] sourceBytes = NBTUtils.writeTagToBytes(source.getTag());
		byte[] destBytes = NBTUtils.writeTagToBytes(dest.getTag());

		CompoundTag sourceMutate = (CompoundTag) NBTUtils.bytesToTag(sourceBytes);
		Chunk sourceChunkMutate = new Chunk(sourceMutate, getX(), getZ(), dest.getLastModified());
		applyDelta(sourceChunkMutate);

		byte[] sourceMutateBytes = NBTUtils.writeTagToBytes(sourceChunkMutate.getTag());
		System.out.println("$$$$$$$$$");
		if (destBytes.length != sourceMutateBytes.length) {
			System.out.println("Chunk delta is invalid in length");
		} else {
			System.out.println("Chunk delta is valid in length");
		}

		if (Arrays.equals(destBytes, sourceMutateBytes)) {
			System.out.println("Chunk delta is identical");
		} else {
			System.out.println("Chunk delta bytes are different! :(");
		}
		System.out.println("$$$$$$$$$");

	}

	public ChunkDelta(byte[] bytes) {
		if (bytes.length == 0) {
			isEmpty = true;
			return;
		}
		ByteBuffer bb = ByteBuffer.wrap(bytes);

		mLightPopulated = new ByteTag("LightPopulated", bb.get());
		mTerrainPopulated = new ByteTag("TerrainPopulated", bb.get());
		mV = new ByteTag("V", bb.get());

		mXPos = new IntTag("xPos", bb.getInt());
		mZPos = new IntTag("zPos", bb.getInt());

		mInhabitedTime = new LongTag("InhabitedTime", bb.getLong());
		mLastUpdate = new LongTag("LastUpdate", bb.getLong());

		byte[] biomeBytes = new byte[256];
		bb.get(biomeBytes);
		mBiomes = new ByteArrayTag("Biomes", biomeBytes);

		// ENTITY
		int entityLength = bb.getInt();
		mEntitiesBytes = new byte[entityLength];
		bb.get(mEntitiesBytes);

		// TILE
		int tileLength = bb.getInt();
		mTileEntitiesBytes = new byte[tileLength];
		bb.get(mTileEntitiesBytes);

		int tileTicksLength = bb.getInt();
		mTileTicksBytes = new byte[tileTicksLength];
		bb.get(mTileTicksBytes);

		// HMDELTA
		int hmLength = bb.getInt() - 128;
		mHeightMapDelta = new HeightMapDelta();
		bb.get(mHeightMapDelta.changeArray);
		mHeightMapDelta.changeValues = new int[hmLength / 4];
		for (int i = 0; i < mHeightMapDelta.changeValues.length; i++) {
			mHeightMapDelta.changeValues[i] = bb.getInt();
		}

		while (bb.hasRemaining()) {
			int length = bb.getInt();
			byte[] sectionBytes = new byte[length];
			bb.get(sectionBytes);
			SectionDelta sectionData = new SectionDelta(sectionBytes);
			mSectionDeltas.add(sectionData);
		}
	}

	private HeightMapDelta calculateHeightMapDelta(int[] sourceHeights, int[] destHeights) {
		HeightMapDelta result = new HeightMapDelta();
		int heightMapLength = 0;
		for (int i = 0; i < sourceHeights.length; i++) {
			if (sourceHeights[i] != destHeights[i]) {
				int bit = i % 8;
				result.changeArray[(int) Math.floor(i / 8d)] |= 1 << bit;
				heightMapLength += 1;
			}
		}
		result.changeValues = new int[heightMapLength];
		int bit = 0;
		for (int i = 0; i < heightMapLength; i++) {
			// loop through each bit in mHeightMapChanges
			while (!isBitSet(result.changeArray, bit)) {
				bit++;
			}
			result.changeValues[i] = mHeightMap.getValue()[bit];
			bit++;
		}
		return result;
	}

	private ArrayList<SectionDelta> calculateSectionDeltas(List<Tag> sourceSections, List<Tag> destSections) {
		// Match sections to each other, and compare, if there are changes create a sectionDelta.
		class Pair {
			CompoundTag sourceTag;

			CompoundTag destTag;

			Pair(CompoundTag sourceTag, CompoundTag destTag) {
				this.sourceTag = sourceTag;
				this.destTag = destTag;
			}
		}

		int changedSections = 0;
		int newSections = 0;
		int deletedSections = 0;

		ArrayList<Pair> pairs = new ArrayList<>();

		ArrayList<SectionDelta> sectionDeltas = new ArrayList<>();

		// If we find a pair, create a delta from both
		for (Tag sourceSection : sourceSections) {
			for (Tag destSection : destSections) {
				Pair pair = new Pair((CompoundTag) sourceSection, (CompoundTag) destSection);
				int sourceY = ((ByteTag) pair.destTag.getValue().get("Y")).getValue();
				int destY = ((ByteTag) pair.sourceTag.getValue().get("Y")).getValue();

				if (sourceY == destY) {
					pairs.add(pair);

					byte[] sBytes = NBTUtils.writeTagToBytes(pair.sourceTag);
					byte[] dBytes = NBTUtils.writeTagToBytes(pair.destTag);

					if (!Arrays.equals(sBytes, dBytes)) {
						SectionDelta delta = new SectionDelta(pair.sourceTag, pair.destTag);
						sectionDeltas.add(delta);
						changedSections++;
					}
				}
			}
		}

		// If there is no pair matching the destination section, this is a new destination section.
		for (Tag tag : destSections) {
			CompoundTag destSection = (CompoundTag) tag;
			boolean foundMatch = false;
			for (Pair pair : pairs) {
				int destY = ((ByteTag) destSection.getValue().get("Y")).getValue();
				int pairY = ((ByteTag) pair.destTag.getValue().get("Y")).getValue();
				if (destY == pairY) {
					foundMatch = true;
				}
			}
			if (!foundMatch) {
				SectionDelta delta = new SectionDelta(destSection);
				sectionDeltas.add(delta);
				newSections++;
			}
		}

		// If there is no pair matching the source section, the section has been removed.
		for (Tag tag : sourceSections) {
			CompoundTag sourceSection = (CompoundTag) tag;
			byte sourceY = ((ByteTag) sourceSection.getValue().get("Y")).getValue();
			boolean foundMatch = false;
			for (Pair pair : pairs) {

				byte pairY = ((ByteTag) pair.sourceTag.getValue().get("Y")).getValue();
				if (sourceY == pairY) {
					foundMatch = true;
				}
			}
			if (!foundMatch) {
				SectionDelta delta = new SectionDelta(sourceY);
				sectionDeltas.add(delta);
				deletedSections++;
			}
		}

		// printSectionDeltaReport(mSectionDeltas.size(), changedSections, newSections, deletedSections);
		return sectionDeltas;

	}

	private void printSectionDeltaReport(int totalSections, int changedSections, int newSections, int deletedSections) {
		System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
		System.out.println("Created " + totalSections + " sections deltas");
		System.out.println(changedSections + " sections changed");
		System.out.println(newSections + " sections created");
		System.out.println(deletedSections + " sections deleted");
		System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
	}

	public byte[] getBytes() {
		if (isEmpty) {
			return new byte[0];
		}
		ByteBuffer bb = ByteBuffer.allocate(getLengthInBytes());

		bb.put(mLightPopulated.getValue()).put(mTerrainPopulated.getValue()).put(mV.getValue());

		bb.putInt(mXPos.getValue()).putInt(mZPos.getValue());

		bb.putLong(mInhabitedTime.getValue()).putLong(mLastUpdate.getValue());

		bb.put(mBiomes.getValue());

		bb.putInt(mEntitiesBytes.length);
		bb.put(mEntitiesBytes);

		bb.putInt(mTileEntitiesBytes.length);
		bb.put(mTileEntitiesBytes);

		if (mTileTicksBytes == null) {
			bb.putInt(0);
		} else {
			bb.putInt(mTileTicksBytes.length);
			bb.put(mTileTicksBytes);
		}

		bb.putInt(mHeightMapDelta.changeArray.length + mHeightMapDelta.changeValues.length * 4);
		bb.put(mHeightMapDelta.changeArray);

		for (int i : mHeightMapDelta.changeValues) {
			bb.putInt(i);
		}

		for (SectionDelta d : mSectionDeltas) {
			bb.putInt(d.getLengthInBytes());
			bb.put(d.getBytes());
		}

		return bb.array();

	}

	public int getLengthInBytes() {
		if (isEmpty) {
			return 0;
		}
		int result = 0;

		result += 3 + 8 + 16 + 256;
		result += mHeightMapDelta.changeArray.length + 4;
		result += mHeightMapDelta.changeValues.length * 4;

		for (SectionDelta d : mSectionDeltas) {
			result += d.getLengthInBytes() + 4;
		}

		result += mEntitiesBytes.length + 4;
		result += mTileEntitiesBytes.length + 4;
		if (mTileTicksBytes == null) {
			result += 4;
		} else {
			result += mTileTicksBytes.length + 4;
		}

		return result;
	}

	private boolean isBitSet(byte[] target, int bitOffset) {
		int index = (int) Math.floor(bitOffset / 8);
		int bitIndex = bitOffset % 8;
		byte result = (byte) ((target[index] >> bitIndex) & 1);
		return result == 1;
	}

	private void setDestinationTags(Chunk dest) {
		CompoundTag tDest = (CompoundTag) dest.getTag().getValue().get("Level");

		mLightPopulated = (ByteTag) tDest.getValue().get("LightPopulated");
		mTerrainPopulated = (ByteTag) tDest.getValue().get("TerrainPopulated");
		mV = (ByteTag) tDest.getValue().get("V");

		mXPos = (IntTag) tDest.getValue().get("xPos");
		mZPos = (IntTag) tDest.getValue().get("zPos");

		mInhabitedTime = (LongTag) tDest.getValue().get("InhabitedTime");
		mLastUpdate = (LongTag) tDest.getValue().get("LastUpdate");

		mEntities = (ListTag) tDest.getValue().get("Entities");
		mTileEntities = (ListTag) tDest.getValue().get("TileEntities");
		mTileTicks = (ListTag) tDest.getValue().get("TileTicks");

		mBiomes = (ByteArrayTag) tDest.getValue().get("Biomes");
		mHeightMap = (IntArrayTag) tDest.getValue().get("HeightMap");
	}

	private byte[] tagToBytes(Tag t) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NBTOutputStream outs = new NBTOutputStream(out);
		outs.writeTag(t);
		outs.close();
		return out.toByteArray();
	}

	public Chunk applyDelta(Chunk c) {
		// TODO clone c.
		CompoundTag chunkTag = c.getTag();
		CompoundTag levelTag = (CompoundTag) chunkTag.getValue().get("Level");

		System.out.println("This delta has an MX of " + mXPos.getValue() + " and a MZ of " + mZPos.getValue());

		// BASIC SET TAGS
		// levelTag.getValue().remove("LightPopulated");
		levelTag.getValue().put("V", mV);
		levelTag.getValue().put("LightPopulated", mLightPopulated);
		levelTag.getValue().put("Biomes", mBiomes);
		levelTag.getValue().put("Entities", NBTUtils.bytesToTag(mEntitiesBytes));
		levelTag.getValue().put("xPos", mXPos);
		levelTag.getValue().put("LastUpdate", mLastUpdate);
		levelTag.getValue().put("zPos", mZPos);
		levelTag.getValue().put("TerrainPopulated", mTerrainPopulated);
		// BASIC SET TAG
		levelTag.getValue().put("TileEntities", NBTUtils.bytesToTag(mTileEntitiesBytes));
		if (mTileTicksBytes != null) {
			levelTag.getValue().put("TileTicks", NBTUtils.bytesToTag(mTileTicksBytes));
		} else {
			levelTag.getValue().remove("TileTicks");
		}
		
		levelTag.getValue().put("InhabitedTime", mInhabitedTime);
		
		// Unwrap heightmap and edit values
		Tag heightMap = levelTag.getValue().get("HeightMap");
		if (heightMap == null) {
			heightMap = new IntArrayTag("HeightMap", new int[256]);
			levelTag.getValue().put("HeightMap", heightMap);
		}

		int[] hmValues = ((IntArrayTag) heightMap).getValue();

		int bit = 0;
		for (int i = 0; i < mHeightMapDelta.changeValues.length; i++) {
			while (!isBitSet(mHeightMapDelta.changeArray, bit)) {
				bit++;
			}
			hmValues[bit] = mHeightMapDelta.changeValues[i];
			bit++;
		}

		levelTag.getValue().put("HeightMap", new IntArrayTag("HeightMap", hmValues));

		//
		//Section unwrap

		ListTag sectionsTag = (ListTag) levelTag.getValue().get("Sections");

		if (sectionsTag == null) {
			List<Tag> sectionsList = new ArrayList<>();
			levelTag.getValue().put("Sections", new ListTag("Sections", CompoundTag.class, sectionsList));
			sectionsTag = (ListTag) levelTag.getValue().get("Sections");

		}
		List<Tag> sections = sectionsTag.getValue();

		// Unwrap Sections
		for (SectionDelta s : mSectionDeltas) {
			Iterator<Tag> tagIterator = sections.iterator();
			boolean foundTag = false;
			while (tagIterator.hasNext()) {
				CompoundTag sectionTag = (CompoundTag) tagIterator.next();
				if (((ByteTag) sectionTag.getValue().get("Y")).getValue() == s.getY()) {
					if (s.getLengthInBytes() == 1) {
						// This tag should be removed
						tagIterator.remove();
						foundTag = true;
					} else {
						// This tag should be delta-d
						s.applyDelta(sectionTag);
						foundTag = true;
					}
				}
			}
			System.out.println("*&* + " + getX() + "  " + getZ() + " -> " + foundTag);
			if (!foundTag) {
				System.out.println(getX() + ", " + getZ() + " creating sections!");
				// Tag should be created and populated by delta
				Map<String, Tag> sectionMap = new HashMap<>();
				sectionMap.put("Y", new ByteTag("Y", (byte) s.getY()));
				sectionMap.put("BlockLight", new ByteArrayTag("BlockLight", new byte[2048]));
				sectionMap.put("Blocks", new ByteArrayTag("Blocks", new byte[4096]));
				sectionMap.put("Data", new ByteArrayTag("Data", new byte[2048]));
				sectionMap.put("SkyLight", new ByteArrayTag("SkyLight", new byte[2048]));

				CompoundTag t = new CompoundTag("", sectionMap);
				s.applyDelta(t);
				sections.add(t);
			}

		}

		return c;
	}

	public int getX() {
		return mXPos.getValue();
	}

	public int getZ() {
		return mZPos.getValue();
	}
}
