package com.kit.video.editor;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * 录音输入处理（从麦克风录音中提取音频数据）
 */
class AudioRecordInputHandler implements InputHandler {

    // 可用的音频源列表
    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean isRecording;

    @Override
    public boolean initialize() {
        try {
            int sampleRateInHz = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

            // 尝试初始化 AudioRecord
            for (final int audioSources : AUDIO_SOURCES) {
                audioRecord = new AudioRecord(audioSources, sampleRateInHz, channelConfig, audioFormat, bufferSize);
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording();
                    isRecording = true;
                    return true;
                }
            }
            Log.e("AudioRecordInputHandler", "AudioRecord initialization failed");
            return false;

        } catch (Exception e) {
            Log.e("AudioRecordInputHandler", "Initialization failed", e);
            return false;
        }
    }

    @Override
    public FrameData getData() {
        if (!isRecording) return null;

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        int bytesRead = audioRecord.read(buffer, bufferSize);
        if (bytesRead > 0) {
            buffer.limit(bytesRead);
            long timestampUs = System.nanoTime() / 1000; // Convert to microseconds
            return new FrameData(buffer, timestampUs);
        } else {
            Log.e("AudioRecordInputHandler", "Error reading audio data: " + bytesRead);
            return null;
        }
    }

    @Override
    public void release() {
        if (isRecording) {
            audioRecord.stop();
            isRecording = false;
        }
        audioRecord.release();
    }
}
