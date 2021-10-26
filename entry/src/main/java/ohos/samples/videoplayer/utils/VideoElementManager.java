/*
 * Copyright (c) 2021 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ohos.samples.videoplayer.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import ohos.aafwk.ability.DataAbilityHelper;
import ohos.aafwk.ability.DataAbilityRemoteException;
import ohos.app.Context;
import ohos.data.resultset.ResultSet;
import ohos.media.common.AVDescription;
import ohos.media.common.sessioncore.AVElement;
import ohos.media.photokit.metadata.AVStorage;
import ohos.samples.videoplayer.vo.MovieVO;
import ohos.samples.videoplayer.vo.PageResultVO;
import ohos.utils.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class is used to prepare audio files for applications.
 */
public class VideoElementManager {
    private static final String TAG = VideoElementManager.class.getSimpleName();
    // private static final String WEB_VIDEO_PATH = "https://ss0.bdstatic.com/-0U0bnSm1A5BphGlnYG/"
    //         + "cae-legoup-video-target/93be3d88-9fc2-4fbd-bd14-833bca731ca7.mp4";
    private static final String WEB_VIDEO_PATH = "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4";
    private final List<AVElement> avElements = new ArrayList<>();

    /**
     * The construction method of this class
     *
     * @param context Context
     */
    public VideoElementManager(Context context) {
        loadFromMediaLibrary(context);
    }

    private void loadFromMediaLibrary(Context context) {
        Uri remoteUri = AVStorage.Video.Media.EXTERNAL_DATA_ABILITY_URI;
        DataAbilityHelper helper = DataAbilityHelper.creator(context, remoteUri, false);
        try {
            ResultSet resultSet = helper.query(remoteUri, null, null);
            LogUtil.info(TAG, "The result size: " + resultSet.getRowCount());
            processResult(resultSet);
            resultSet.close();
        } catch (DataAbilityRemoteException e) {
            LogUtil.error(TAG, "Query system media failed.");
        } finally {
            helper.release();
        }
    }

    private void processResult(ResultSet resultSet) {
        while (resultSet.goToNextRow()) {
            String path = resultSet.getString(resultSet.getColumnIndexForName(AVStorage.AVBaseColumns.DATA));
            String title = resultSet.getString(resultSet.getColumnIndexForName(AVStorage.AVBaseColumns.TITLE));
            AVDescription bean =
                    new AVDescription.Builder().setTitle(title).setIMediaUri(Uri.parse(path)).setMediaId(path).build();
            avElements.add(new AVElement(bean, AVElement.AVELEMENT_FLAG_PLAYABLE));
        }
        writeDefault();
    }

    private void writeDefault() {
        HashMap<String, Object> map = new HashMap<>();
        // TODO 请求后端文件服务,获取视频播放列表
        HttpUtils.ResponseWrap responseWrap = HttpUtils.get("http://192.168.91.100/video/list/page").setParameters(map).execute();
        if (responseWrap.isSuccess()) {
            PageResultVO<MovieVO> resultVO = responseWrap.getJsonObject(new TypeReference<PageResultVO<MovieVO>>() {
            });
            List<MovieVO> data = resultVO.getData();
            for (MovieVO movieVO : data) {
                AVDescription bean =
                        new AVDescription.Builder()
                                .setTitle(movieVO.getName())
                                .setIMediaUri(Uri.parse(movieVO.getUrl()))
                                .setMediaId(movieVO.getUrl())
                                .build();
                avElements.add(new AVElement(bean, AVElement.AVELEMENT_FLAG_PLAYABLE));
            }
        }
    }

    /**
     * get the list of videoElements
     *
     * @return videoElements the list of videoElements
     */
    public List<AVElement> getAvElements() {
        return avElements;
    }
}
