package com.cusackj.mcad.model;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTOutputStream;
import org.jnbt.NBTUtils;
import org.jnbt.RegionFile;

import com.cusackj.mcad.debug.ChunkCompare;

public class Region {
	private RegionFile mRegionFile;
	private int mX;
	private int mZ;
	private List<Chunk> mChunks = new ArrayList<Chunk>();

	public Region(File f) {
		String[] parts = f.getName().split("\\.");
		if (parts.length != 4 || !parts[0].equalsIgnoreCase("r") || !parts[3].equalsIgnoreCase("mca")) {
			System.out.println((parts.length != 4) + " " + !parts[0].equalsIgnoreCase("r") + " " + !parts[3].equalsIgnoreCase("mca"));
			throw new IllegalArgumentException("Filename must follow the format r.x.z.mca!");
		}
		mX = Integer.parseInt(parts[1]);
		mZ = Integer.parseInt(parts[2]);
		mRegionFile = new RegionFile(f);

		readData();
	}

	public Region() {
		// TODO Auto-generated constructor stub
	}

	public int getNumberOfChunks() {
		return mChunks.size();
	}

	private void readData() {
		for (int x = mX * 32; x < mX * 32 + 32; x++) {
			for (int z = mZ * 32; z < mZ * 32 + 32; z++) {
				// For each chunk that exists, extract NBT data
				if (mRegionFile.hasChunk(x, z)) {
					try {
						long chunkLastModified = mRegionFile.getTimeStamp(x, z);
						NBTInputStream in = new NBTInputStream(mRegionFile.getChunkDataInputStream(x, z));
						CompoundTag chunkTag = (CompoundTag) in.readTag();

						mChunks.add(new Chunk(chunkTag, x, z, chunkLastModified));
						in.close();
					} catch (Exception e) {
						System.out.println("Could not load chunk [" + x + ", " + z + "].");
						e.printStackTrace();
					}
				}
			}
		}
		System.out.println("Loaded " + mChunks.size() + " chunks.");
	}

	public int getX() {
		return mX;
	}

	public int getZ() {
		return mZ;
	}

	public void write(File file) throws IOException {
		RegionFile regionFile = new RegionFile(file);
		for (Chunk ac : mChunks) {
			NBTOutputStream outs = new NBTOutputStream(regionFile.getChunkDataOutputStream(ac.getX(), ac.getZ(), ac.getLastModified()));
			outs.writeTag(ac.getTag());
			outs.close();
			ChunkCompare.compareBytes(ac.getX(), ac.getZ(), NBTUtils.writeTagToBytes(ac.getTag()));
			if(ac.getX()!=NBTUtils.getXPosFromChunkTag(ac.getTag()) ||ac.getZ() != NBTUtils.getZPosFromChunkTag(ac.getTag())){
				System.out.println("are these equal? " + ac.getX() + "=" + NBTUtils.getXPosFromChunkTag(ac.getTag()) + ", " + ac.getZ() + "=" + NBTUtils.getZPosFromChunkTag(ac.getTag()));
			}
			
		}
		System.out.println("Wrote " + mChunks.size() + " chunks to a region file");
	}

	public long getLastModified() {
		return mRegionFile.lastModified();
	}

	public Chunk getChunk(int x, int z) {
		for (Chunk c : mChunks) {
			if (c.getX() == x && c.getZ() == z) {
				return c;
			}
		}
		return null;
	}

	public void setChunk(Chunk c) {
		if (getChunk(c.getX(), c.getZ()) == null) {
			mChunks.add(c);
		} else {
			mChunks.remove(getChunk(c.getX(), c.getZ()));
			mChunks.add(c);
		}
	}
	
	public void removeChunk(int x, int z){
		Chunk toRemove = getChunk(x, z);
		if(toRemove!=null) mChunks.remove(toRemove);
	}

	public boolean hasChunk(int x, int z) {
		return (getChunk(x, z) != null);
	}

	public long getChunkTimestamp(int x, int z) {
		if (hasChunk(x, z)) {
			return getChunk(x, z).getLastModified();
		} else {
			return -1;
		}
	}

}
