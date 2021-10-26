package ohos.samples.videoplayer.vo;

public class GoFastVO {

    /**
     * url : http://192.168.1.15:8080/group1/default/20191130/16/17/2/720P_1500K_72792742.mp4
     * md5 : 57e1d02d41a7c69b5c7b761481bc34a5
     * path : /group1/default/20191130/16/17/2/720P_1500K_72792742.mp4
     * domain : http://192.168.1.15:8080
     * scene : default
     * size : 79886770
     * mtime : 1575101854
     * scenes : default
     * retmsg :
     * retcode : 0
     * src : /group1/default/20191130/16/17/2/720P_1500K_72792742.mp4
     */

    private String url;
    private String md5;
    private String path;
    private String domain;
    private String scene;
    private Long size;
    private Long mtime;
    private String scenes;
    private String retmsg;
    private Long retcode;
    private String src;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getMtime() {
        return mtime;
    }

    public void setMtime(Long mtime) {
        this.mtime = mtime;
    }

    public String getScenes() {
        return scenes;
    }

    public void setScenes(String scenes) {
        this.scenes = scenes;
    }

    public String getRetmsg() {
        return retmsg;
    }

    public void setRetmsg(String retmsg) {
        this.retmsg = retmsg;
    }

    public Long getRetcode() {
        return retcode;
    }

    public void setRetcode(Long retcode) {
        this.retcode = retcode;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }
}