package com.beijunyi.sw.output;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

import com.beijunyi.sw.config.Settings;
import com.beijunyi.sw.output.models.Texture;
import com.beijunyi.sw.sa.SaResourcesManager;
import com.beijunyi.sw.sa.models.AdrnBlock;
import com.beijunyi.sw.sa.models.RealBlock;
import com.beijunyi.sw.utils.BitConverter;
import com.beijunyi.sw.utils.ImageUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

@Named
public class TextureManager {

  private final SaResourcesManager srm;
  private final Path textureDir;
  private final Kryo kryo;

  private Map<Integer, Texture> textures = new HashMap<>();

  @Inject
  public TextureManager(Settings settings, SaResourcesManager srm, Kryo kryo) throws IOException {
    this.srm = srm;
    this.kryo = kryo;
    textureDir = settings.getOutputPath().resolve("textures");
    Files.createDirectories(textureDir);
  }

  private Path getOutputTexturePath(int id) {
    return textureDir.resolve(id + ".bin");
  }

  public Texture getTexture(int id) {
    Texture texture = textures.get(id);
    if(texture == null) {
      synchronized(this) {
        // try again
        texture = textures.get(id);
        if(texture != null)
          return texture;

        Path outputTexturePath = getOutputTexturePath(id);
        if(Files.exists(outputTexturePath)) {
          try(InputStream input = Files.newInputStream(outputTexturePath)) {
            texture = kryo.readObject(new Input(input), Texture.class);
          } catch(IOException e) {
            throw new RuntimeException("Could not read " + outputTexturePath, e);
          }
        } else {
          AdrnBlock adrn = srm.getAdrnBlock(id);
          RealBlock real = srm.getRealBlock(adrn.getAddress(), adrn.getSize());
          texture = createTexture(adrn, real);
          try(OutputStream output = Files.newOutputStream(outputTexturePath)) {
            kryo.writeObject(new Output(output), texture);
          } catch(IOException e) {
            throw new RuntimeException("Could not write " + outputTexturePath, e);
          }
        }
        textures.put(id, texture);
      }
    }
    return texture;
  }

  private static Texture createTexture(AdrnBlock adrn, RealBlock real) {
    Texture texture = new Texture();
    texture.setX(adrn.getxOffset());
    texture.setY(adrn.getyOffset());
    texture.setWidth(adrn.getWidth());
    texture.setHeight(adrn.getHeight());
    byte[] buf;
    if(real.getMajor() == 1) {
      buf = new byte[adrn.getWidth() * adrn.getHeight()];
      decodeRunLength(real.getData(), buf);
    }
    else
      buf = real.getData();
    flipVertical(buf, adrn.getWidth(), adrn.getHeight());
    texture.setBitmap(buf);
    return texture;
  }

  private static void decodeRunLength(byte[] rl, byte[] buf) {
    int length = rl.length;
    int readPos = 0;
    int writePos = 0;
    while(readPos < length) {
      short head = BitConverter.uint8(rl[readPos++]);
      byte value = 0;
      boolean copy;
      short x, y, z;
      if(head >= 224) {
        copy = false;
        value = 0;
        x = (short) (head - 224);
        y = BitConverter.uint8(rl[readPos++]);
        z = BitConverter.uint8(rl[readPos++]);
      } else if(head >= 208) {
        copy = false;
        value = 0;
        x = 0;
        y = (short) (head - 208);
        z = BitConverter.uint8(rl[readPos++]);
      } else if(head >= 192) {
        copy = false;
        value = 0;
        x = 0;
        y = 0;
        z = (short) (head - 192);
      } else if(head >= 160) {
        copy = false;
        value = rl[readPos++];
        x = (short) (head - 160);
        y = BitConverter.uint8(rl[readPos++]);
        z = BitConverter.uint8(rl[readPos++]);
      } else if(head >= 144) {
        copy = false;
        value = rl[readPos++];
        x = 0;
        y = (short) (head - 144);
        z = BitConverter.uint8(rl[readPos++]);
      } else if(head >= 128) {
        copy = false;
        value = rl[readPos++];
        x = 0;
        y = 0;
        z = (short) (head - 128);
      } else if(head >= 32) {
        copy = true;
        x = (short) (head - 32);
        y = BitConverter.uint8(rl[readPos++]);
        z = BitConverter.uint8(rl[readPos++]);
      } else if(head >= 16) {
        copy = true;
        x = 0;
        y = (short) (head - 16);
        z = BitConverter.uint8(rl[readPos++]);
      } else {
        copy = true;
        x = 0;
        y = 0;
        z = head;
      }
      int total = x * 65536 + y * 256 + z;
      int canWrite = buf.length - writePos;
      if(total > canWrite) {
        total = canWrite;
      }
      if(copy) {
        int canRead = length - readPos;
        if(total > canRead) {
          total = canRead;
        }
        for(int i = 0; i < total; i++) {
          value = rl[readPos++];
          buf[writePos++] = value;
        }
      } else {
        for(int i = 0; i < total; i++) {
          buf[writePos++] = value;
        }
      }
    }
  }

  private static void flipVertical(byte[] data, int width, int height) {
    byte[] buf = new byte[width];
    for(int i = 0; i < height / 2; i++) {
      System.arraycopy(data, i * width, buf, 0, width);
      System.arraycopy(data, (height - i - 1) * width, data, i * width, width);
      System.arraycopy(buf, 0, data, (height - i - 1) * width, width);
    }
  }

}
