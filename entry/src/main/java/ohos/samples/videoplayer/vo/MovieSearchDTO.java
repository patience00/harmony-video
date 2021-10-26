package ohos.samples.videoplayer.vo;

public class MovieSearchDTO extends PageForm {


    private String name;
    private Long tagId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }
}