package io.javaoperatorsdk.operator.sample;

public class OracleCMPStatus {

  private String targetUri;

  private String dbStatus;

  public String getTargetUri() {
    return targetUri;
  }

  public void setTargetUri(String uri) {
    this.targetUri = uri;
  }

  public String getDbStatus() {
    return dbStatus;
  }

  public void setDbStatus(String areWeGood) {
    this.dbStatus = areWeGood;
  }

}
