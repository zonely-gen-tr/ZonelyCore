package dev.zonely.whiteeffect.nms.v1_8_R3.network;

import io.netty.channel.*;

import java.net.SocketAddress;
public class EmptyChannel extends AbstractChannel {
   public EmptyChannel() {
      super((Channel)null);
   }

   public ChannelConfig config() {
      return null;
   }

   public boolean isActive() {
      return false;
   }

   public boolean isOpen() {
      return false;
   }

   public ChannelMetadata metadata() {
      return null;
   }

   protected void doBeginRead() throws Exception {
   }

   protected void doBind(SocketAddress arg0) throws Exception {
   }

   protected void doClose() throws Exception {
   }

   protected void doDisconnect() throws Exception {
   }

   protected void doWrite(ChannelOutboundBuffer arg0) throws Exception {
   }

   protected boolean isCompatible(EventLoop arg0) {
      return false;
   }

   protected SocketAddress localAddress0() {
      return null;
   }

   protected AbstractUnsafe newUnsafe() {
      return null;
   }

   protected SocketAddress remoteAddress0() {
      return null;
   }
}
