import imageCompression from 'browser-image-compression';
import type { LabAnswerImageMeta } from '@/types/lab';

const MAX_BYTES = 1_048_576;
const MAX_EDGE_STEPS = [1920, 1280, 1024] as const;
const QUALITY_STEPS = [0.85, 0.75, 0.65, 0.55] as const;

export interface CompressedLabAnswerImageResult {
  file: File;
  compressed: boolean;
  originalSize: number;
}

const readImageDimensions = (file: File) =>
  new Promise<{ width: number; height: number }>((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();

    image.onload = () => {
      URL.revokeObjectURL(objectUrl);
      resolve({ width: image.naturalWidth, height: image.naturalHeight });
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error('无法读取图片，请换一张后重试'));
    };
    image.src = objectUrl;
  });

const needsCompression = async (file: File) => {
  if (file.size > MAX_BYTES) {
    return true;
  }

  const { width, height } = await readImageDimensions(file);
  return Math.max(width, height) > MAX_EDGE_STEPS[0];
};

const toJpegFile = (blob: Blob, originalName: string) => {
  const baseName = originalName.replace(/\.[^.]+$/, '') || 'image';
  return new File([blob], `${baseName}.jpg`, { type: 'image/jpeg', lastModified: Date.now() });
};

export const compressLabAnswerImage = async (file: File): Promise<CompressedLabAnswerImageResult> => {
  const originalSize = file.size;
  if (!(await needsCompression(file))) {
    return { file, compressed: false, originalSize };
  }

  for (const maxWidthOrHeight of MAX_EDGE_STEPS) {
    for (const quality of QUALITY_STEPS) {
      const compressedBlob = await imageCompression(file, {
        maxSizeMB: MAX_BYTES / (1024 * 1024),
        maxWidthOrHeight,
        useWebWorker: true,
        initialQuality: quality,
        fileType: 'image/jpeg',
      });
      const compressedFile = compressedBlob instanceof File
        ? compressedBlob
        : toJpegFile(compressedBlob, file.name);

      if (compressedFile.size <= MAX_BYTES) {
        return {
          file: compressedFile.type === 'image/jpeg'
            ? compressedFile
            : toJpegFile(compressedFile, file.name),
          compressed: true,
          originalSize,
        };
      }
    }
  }

  throw new Error('图片过大，请压缩或裁剪后重试');
};

export const toLabAnswerImageMeta = (
  uploaded: Pick<LabAnswerImageMeta, 'path' | 'name' | 'contentType' | 'size'>,
  extras?: Pick<LabAnswerImageMeta, 'compressed' | 'originalSize'>,
): LabAnswerImageMeta => ({
  path: uploaded.path,
  name: uploaded.name,
  contentType: uploaded.contentType,
  size: uploaded.size,
  compressed: extras?.compressed ?? false,
  originalSize: extras?.originalSize,
});
