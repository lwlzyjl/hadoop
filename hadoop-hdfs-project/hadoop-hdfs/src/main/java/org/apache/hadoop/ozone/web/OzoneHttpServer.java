/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.ozone.web;

import com.google.common.base.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSUtil;
import org.apache.hadoop.http.HttpConfig;
import org.apache.hadoop.http.HttpServer2;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.ozone.client.OzoneClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Base class for HTTP server of the Ozone related components.
 */
public abstract class OzoneHttpServer {

  private static final Logger LOG =
      LoggerFactory.getLogger(OzoneHttpServer.class);

  private HttpServer2 httpServer;
  private final Configuration conf;

  private InetSocketAddress httpAddress;
  private InetSocketAddress httpsAddress;

  private HttpConfig.Policy policy;

  private String name;

  public OzoneHttpServer(Configuration conf, String name) throws IOException {
    this.name = name;
    this.conf = conf;
    if (isEnabled()) {
      policy = DFSUtil.getHttpPolicy(conf);
      if (policy.isHttpEnabled()) {
        this.httpAddress = getHttpBindAddress();
      }
      if (policy.isHttpsEnabled()) {
        this.httpsAddress = getHttpsBindAddress();
      }
      HttpServer2.Builder builder = null;
      builder = DFSUtil.httpServerTemplateForNNAndJN(conf, this.httpAddress,
          this.httpsAddress, name, getSpnegoPrincipal(), getKeytabFile());
      httpServer = builder.build();

    }

  }

  protected InetSocketAddress getBindAddress(String bindHostKey,
      String addressKey, String bindHostDefault, int bindPortdefault) {
    final Optional<String> bindHost =
        OzoneClientUtils.getHostNameFromConfigKeys(conf, bindHostKey);

    final Optional<Integer> addressPort =
        OzoneClientUtils.getPortNumberFromConfigKeys(conf, addressKey);

    final Optional<String> addresHost =
        OzoneClientUtils.getHostNameFromConfigKeys(conf, addressKey);

    String hostName = bindHost.or(addresHost).or(bindHostDefault);

    return NetUtils.createSocketAddr(
        hostName + ":" + addressPort.or(bindPortdefault));
  }

  /**
   * Retrieve the socket address that should be used by clients to connect
   * to the  HTTPS web interface.
   *
   * @return Target InetSocketAddress for the Ozone HTTPS endpoint.
   */
  public InetSocketAddress getHttpsBindAddress() {
    return getBindAddress(getHttpsBindHostKey(), getHttpsAddressKey(),
        getBindHostDefault(), getHttpsBindPortDefault());
  }

  /**
   * Retrieve the socket address that should be used by clients to connect
   * to the  HTTP web interface.
   * <p>
   * * @return Target InetSocketAddress for the Ozone HTTP endpoint.
   */
  public InetSocketAddress getHttpBindAddress() {
    return getBindAddress(getHttpBindHostKey(), getHttpAddressKey(),
        getBindHostDefault(), getHttpBindPortDefault());

  }

  public void start() throws IOException {
    if (httpServer != null && isEnabled()) {
      httpServer.start();
      updateConnectorAddress();
    }

  }

  private boolean isEnabled() {
    return conf.getBoolean(getEnabledKey(), true);
  }

  public void stop() throws Exception {
    if (httpServer != null) {
      httpServer.stop();
    }
  }

  /**
   * Update the configured listen address based on the real port
   * <p>
   * (eg. replace :0 with real port)
   */
  public void updateConnectorAddress() {
    int connIdx = 0;
    if (policy.isHttpEnabled()) {
      httpAddress = httpServer.getConnectorAddress(connIdx++);
      String realAddress = NetUtils.getHostPortString(httpAddress);
      conf.set(getHttpAddressKey(), realAddress);
      LOG.info(
          String.format("HTTP server of SCM is listening at http://%s",
              realAddress));
    }

    if (policy.isHttpsEnabled()) {
      httpsAddress = httpServer.getConnectorAddress(connIdx);
      String realAddress = NetUtils.getHostPortString(httpsAddress);
      conf.set(getHttpsAddressKey(), realAddress);
      LOG.info(
          String.format("HTTP server of SCM is listening at https://%s",
              realAddress));
    }
  }

  public InetSocketAddress getHttpAddress() {
    return httpAddress;
  }

  public InetSocketAddress getHttpsAddress() {
    return httpsAddress;
  }

  protected abstract String getHttpAddressKey();

  protected abstract String getHttpsAddressKey();

  protected abstract String getHttpBindHostKey();

  protected abstract String getHttpsBindHostKey();

  protected abstract String getBindHostDefault();

  protected abstract int getHttpBindPortDefault();

  protected abstract int getHttpsBindPortDefault();

  protected abstract String getKeytabFile();

  protected abstract String getSpnegoPrincipal();

  protected abstract String getEnabledKey();

}