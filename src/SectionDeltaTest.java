import static org.junit.Assert.*;

import java.io.File;

import org.jnbt.CompoundTag;
import org.jnbt.ListTag;
import org.jnbt.NBTUtils;
import org.junit.Test;

import com.cusackj.mcad.delta.SectionDelta;
import com.cusackj.mcad.model.Chunk;
import com.cusackj.mcad.model.Region;


public class SectionDeltaTest {

	@Test
	public void test() {
		
		Region r = new Region(new File("source/r.0.0.mca"));
		Region r2 = new Region(new File("destination/r.0.0.mca"));
		
		Chunk sourceC = r.getChunk(0, 4);
		Chunk destC = r2.getChunk(0, 4);
		
		
		CompoundTag source = getSection(sourceC.getTag());
		CompoundTag dest = getSection(destC.getTag());
		
		SectionDelta delta = new SectionDelta(source, dest);
		
		CompoundTag sourceMutate = getSection(sourceC.getTag());
		
		delta.applyDelta(sourceMutate);
		
		assertArrayEquals(NBTUtils.writeTagToBytes(dest), NBTUtils.writeTagToBytes(sourceMutate));
	}
	
	private CompoundTag getSection(CompoundTag chunk){
		CompoundTag level =(CompoundTag) chunk.getValue().get("Level");
		ListTag l = (ListTag) level.getValue().get("Sections");
		return (CompoundTag) l.getValue().get(0);
		
	}

}
