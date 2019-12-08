package acpcommander;

/**
 * <p>Beschreibung: Core class for sending ACP commands to Buffalo Linkstation (R). Out
 * of the work of linkstationwiki.net</p>
 *
 * <p>Copyright: Copyright (c) 2006, GPL</p>
 *
 * <p>Organisation: linkstationwiki.net</p>
 *
 * @author Georg
 * @version 0.4.1 (beta)
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class ACP {
  private InetAddress target;
  protected Integer Port = new Integer(22936);
  private String connID; // connection ID, "unique" identifier for the connection
  private String targetmac; // MAC address of the LS, it reacts only if correct MAC or
  // FF:FF:FF:FF:FF:FF is set in the packet
  protected byte[] Key = new byte[4]; // Key for password encryption
  // sent in reply to ACP discovery packet
  protected String password;
  private String ap_servd = "ap_servd";
  private InetSocketAddress bind;
  private Charset defaultCharset = Charset.forName("UTF-8");

  /** set socket timeout to 1000 ms, rather high, but some users report timeout
  * problems. Could also be UDP-related - try resending packets
  * Especially blinkled, saveconfig, loadconfig have long reply times as reply is
  * sent when the command has been executed. Same has to be considered for other cmds.
  */
  protected int Timeout = 1000;
  protected int resendPackets = 2; // standard value for repeated sending of packets

  public int debuglevel = 0; // Debug level

  protected int rcvBufLen = 4096; // standard length of receive buffer

  public ACP() {
  }

  public ACP(String Target) {
    this();
    setTarget(Target);
  }

  public ACP(byte[] Target) {
    this();
    setTarget(Target);
  }


  //
  //  set/get for private variables
  //
  public String getconnid() {
    return connID.toString();
  }

  public void setconnid(String connectionid) {
    // TODO: input param checking!
    connID = connectionid;
  }

  public void setconnid(byte[] connectionid) {
    // TODO: input param checking!
    connID = bufferToHex(connectionid, 0, 6);
  }

  public String getTargetMAC() {
    return (targetmac.toString());
  }

  public void setTargetMAC(String TargetMAC) {
    // TODO: input param checking!
    targetmac = TargetMAC;
  }

  public byte[] getTargetKey() {
    byte[] result = new byte[Key.length];
    System.arraycopy(Key,0,result,0,Key.length);
    return (result);
  }

  public void setTargetKey(byte[] _Key) {
    // TODO: input param checking!
    if (_Key.length != 4) {
      outError("ACPException: Encryption key must be 4 bytes long!");
      return;
    }
    System.arraycopy(_Key,0,Key,0,_Key.length);
  }

  public void setTargetKey(String _Key) {
    // TODO: input param checking!
    setTargetKey(hextobyte(_Key));
  }

  public void setPassword(String _password) {
    password = _password;
  }

  public InetAddress getTarget() {
    return target;
  }

  public void setTarget(String Target) {
    try {
      target = InetAddress.getByName(Target);
    } catch (UnknownHostException ex) {
      outInfoSetTarget();
      outError(ex.toString() + " [in setTarget]");
    }
  }

  public void setTarget(byte[] Target) {
    try {
      target = InetAddress.getByAddress(Target);
    } catch (UnknownHostException ex) {
      outInfoSetTarget();
      outError(ex.toString() + " [in setTarget]");
    }
  }

  public void setbroadcastip(String Target) {
    try {
      target = InetAddress.getByName(Target);
      setTargetMAC("FF:FF:FF:FF:FF:FF");
    } catch (UnknownHostException ex) {
      outError(ex.toString() + " [in setbroadcastip]");
    }
  }

  public void setbroadcastip(byte[] Target) {
    try {
      target = InetAddress.getByAddress(Target);
      setTargetMAC("FF:FF:FF:FF:FF:FF");
    } catch (UnknownHostException ex) {
      outError(ex.toString() + " [in setbroadcastip]");
    }
  }

  public void bind(InetSocketAddress localip) {
    bind = localip;
    if (localip.isUnresolved()) {
      outWarning("The bind address " + localip
          + " given with parameter -b could not be resolved to a local IP-Address.\n"
          + "You must use this parameter with a valid IP-Address that belongs to "
          + "the PC you run acp_commander on.\n");
      bind = null;
    }
  }

  public void bind(String localip) {
    // bind socket to a local address (-b)
    // Create a socket address from a hostname (_bind) and a port number. A port number
    // of zero will let the system pick up an ephemeral port in a bind operation.
    if (!localip.equalsIgnoreCase("")) {
      bind(new InetSocketAddress(localip, 0));
    } else {
      bind = null;
    }
  }

  int getdebuglevel() {
    return debuglevel;
  }

  //
  // ACP functionallity
  //

  public String[] find() {
    // discover linkstations by sending an ACP-Discover package
    // return on line of formatted string per found LS
    return doDiscover();
  }

  public String[] command(String cmd, int maxResend) {
    // send telnet-type command cmd to Linkstation by ACPcmd
    enonecmd();
    authent();
    if (maxResend <= 0) {
      maxResend = resendPackets;
    }
    return doSendRcv(getacpcmd(connID, targetmac, cmd), maxResend);
  }

  public String[] command(String cmd) {
    // send telnet-type command cmd to Linkstation by ACPcmd - only send packet once!
    enonecmd();
    authent();
    return doSendRcv(getacpcmd(connID, targetmac, cmd), 1);
  }

  public String[] authent() {
    byte[] _encrypted = encryptacppassword(password, Key);
    return authent(_encrypted);
  }

  public String[] authent(byte[] enc_password) {
    // authenticate to ACP protokoll
    return doSendRcv(getacpauth(connID, targetmac, enc_password));
  }

  public String[] shutdown() {
    // ENOneCmd protected
    return doSendRcv(getacpshutdown(connID, targetmac));
  }

  public String[] reboot() {
    // ENOneCmd protected
    return doSendRcv(getacpreboot(connID, targetmac));
  }

  public String[] EMMode() {
    // ENOneCmd protected
    return doSendRcv(getacpEMMode(connID, targetmac));
  }

  public String[] normmode() {
    // ENOneCmd protected
    return doSendRcv(getacpnormmode(connID, targetmac));
  }

  public String[] blinkled() {
    int _mytimeout = Timeout;
    Timeout = 60000;
    String[] result = doSendRcv(getacpblinkled(connID, targetmac));
    Timeout = _mytimeout;
    return result;
  }

  public String[] enonecmd() {
    return enonecmdenc(encryptacppassword(ap_servd, Key));
  }

  public String[] enonecmdenc(byte[] encPassword) {
    return doSendRcv(getacpenonecmd(connID, targetmac, encPassword));
  }

  public String[] saveconfig() {
    // set timeout to 1 min
    int _mytimeout = Timeout;
    Timeout = 60000;
    String[] result = doSendRcv(getacpsaveconfig(connID, targetmac));
    Timeout = _mytimeout;
    return result;
  }

  public String[] loadconfig() {
    // set timeout to 1 min
    int _mytimeout = Timeout;
    Timeout = 60000;
    String[] result = doSendRcv(getacploadconfig(connID, targetmac));
    Timeout = _mytimeout;
    return result;
  }

  public String[] debugmode() {
    return doSendRcv(getacpdebugmode(connID, targetmac));
  }

  public String[] multilang(byte Language) {
    // interface to switch web GUI language
    // ENOneCmd protected
    // 0 .. Japanese
    // 1 .. English
    // 2 .. German
    // default .. English
    return doSendRcv(getacpmultilang(connID, targetmac, Language));
  }

  public String[] changeip(byte[] newip, byte[] newMask, boolean usedhcp) {
    // change IP address
    byte[] _encrypted = encryptacppassword(password, Key);
    return doSendRcv(getacpchangeip(connID, targetmac, newip, newMask, usedhcp, _encrypted));
  }

  //--- End of public routines ---

  //
  // ACP-Interface functions (private)
  //

  private String[] doDiscover() {
    String _state = "[Send/Receive ACPDiscover]";
    byte[] buf = getacpdisc(connID, targetmac);
    byte[] buf2 = getacpdisc2(connID, targetmac);
    String[] _searchres = new String[1];
    ArrayList<String> _tempres = new ArrayList<>();
    DatagramSocket _socket;

    DatagramPacket _packet = new DatagramPacket(buf, buf.length, target, Port.intValue());
    DatagramPacket _receive = new DatagramPacket(new byte[rcvBufLen], rcvBufLen);

    DatagramPacket _packet2 = new DatagramPacket(buf2, buf2.length, target, Port.intValue());

    try {
      _socket = getSocket(); // TODO bind functionality is missing here

      _socket.send(_packet);
      _socket.send(_packet2);

      long _LastSendTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - _LastSendTime < Timeout) {
        _socket.receive(_receive);
        _searchres = rcvacp(_receive.getData(), debuglevel); // get search results

        // TODO: do optional Discover event with _searchres
        _tempres.add(_searchres[1]); // add formatted string to result list
      }
    } catch (java.net.SocketTimeoutException SToE) {
      // TimeOut should be OK as we wait until Timeout if we get packets
      outDebug(
          "Timeout reached, stop listening to further Discovery replies",
                2);
    } catch (java.net.SocketException SE) {
      // TODO: better error handling
      outInfoSocket();
      outError("Exception: SocketException (" + SE.getMessage() + ") "
                + _state);
    } catch (java.io.IOException IOE) {
      // TODO: better error handling
      outError("Exception: IOException (" + IOE.getMessage() + ") "
                + _state);
    }

    // first check for repeated entries and delete them.
    for (int i = 0; i < _tempres.size() - 1; i++) {
      for (int j = i + 1; j < _tempres.size(); j++) {
        // if entry i is equal to entry j
        if (((String) _tempres.get(i)).equals((String) _tempres.get(j))) {
          // remove j, alternatively clear string and delete in second loop
          _tempres.remove(j);
          j--;
        }
      }
    }

    // move results into string array
    String[] result = new String[_tempres.size()];
    for (int i = 0; i < _tempres.size(); i++) {
      result[i] = (String) _tempres.get(i);
    }

    //probably not good practice and should be refactored
    if (target.toString().split("/",2)[1].equals("255.255.255.255")) {
      return result;
    }
    return _searchres;
  }

  // send ACP packet and handle answer
  private String[] doSendRcv(byte[] buf) {
    return doSendRcv(buf, resendPackets);
  }


  private String[] doSendRcv(byte[] buf, int repeatSend) {
    String _ACPcmd = bufferToHex(buf, 9, 1) + bufferToHex(buf, 8, 1);
    String _state = "[ACP Send/Receive (Packet:" + _ACPcmd + " = "
            + getcmdstring(buf) + ")]";
    String[] result;
    int sendcount = 0;
    boolean SendAgain = true;
    DatagramSocket _socket;
    DatagramPacket _packet = new DatagramPacket(buf, buf.length, target, Port.intValue());
    // TODO: danger - possible buffer overflow/data loss with fixed packet length
    DatagramPacket _receive = new DatagramPacket(new byte[rcvBufLen], rcvBufLen);

    do {
      sendcount++;
      try {
        outDebug("Sending " + sendcount + "/" + repeatSend, 2);

        _socket = getSocket();

        _socket.send(_packet);
        _socket.receive(_receive);

        SendAgain = false; // we received an answer

            // TODO: do optional Receive-event with result
      } catch (java.net.SocketTimeoutException SToE) {
        // TODO: better error handling
        result = new String[2];
        if (sendcount >= repeatSend) {
          result[1] = "Exception: SocketTimeoutException (" + SToE.getMessage() + ") " + _state;

          outInfoTimeout();
          outError(result[1]);
        } else {
          result[1] = "Timeout (" + _state + " retry sending ("
                + sendcount + "/" + repeatSend + ")";
          outDebug(result[1], 1);
        }
      } catch (java.net.SocketException SE) {
        // TODO: better error handling
        result = new String[2];
        result[1] = "Exception: SocketException (" + SE.getMessage() + ") " + _state;

        outInfoSocket();
        outError(result[1]);
      } catch (java.io.IOException IOE) {
        // TODO: better error handling
        result = new String[2];
        result[1] = "Exception: IOException (" + IOE.getMessage() + ") " + _state;
        outError(result[1]);
      }

    } while ((sendcount < repeatSend) && SendAgain); // repeat until max retries reached

    result = rcvacp(_receive.getData(), debuglevel); // get search results

    return result;
  }

  private DatagramSocket getSocket() throws java.net.SocketException {
    DatagramSocket _socket;
    if (bind != null) {
      // bind socket to a local address (-b)
      // Create a socket address from a hostname (_bind) and a port number. A port number
      // of zero will let the system pick up an ephemeral port in a bind operation.
      outDebug("Binding socket to: " + bind.toString() + "\n", 1);

      _socket = new DatagramSocket(bind);
    } else {
      _socket = new DatagramSocket();
    }

    _socket.setSoTimeout(Timeout);
    return _socket;
  }

  //
  // ACP packet creation functionality
  //

  private int getcommand(byte[] buf) {
    return (int) ((buf[9] & 0xFF) << 8) + (int) (buf[8] & 0xFF);
  }

  private byte getSpecialCmd(byte[] buf) {
    return buf[32];
  }

  private String getcmdstring(byte[] buf) {
    int acpcmd = getcommand(buf);
    String cmdstring = String.valueOf("");

    switch (acpcmd) {
        // ACP_Commands
        // Currently missing, but defined in clientUtil_server:
        //     ACP_FORMAT
        //     ACP_ERASE_USER
        // missing candidates are 0x80C0 and 0x80D0 or 0x8C00 and 0x8D00

      case 0x8020:
        cmdstring = "ACP_Discover";
        break;
      case 0x8030:
        cmdstring = "ACP_Change_IP";
        break;
      case 0x8040:
        cmdstring = "ACP_Ping";
        break;
      case 0x8050:
        cmdstring = "ACP_Info";
        break;
      case 0x8070:
        cmdstring = "ACP_FIRMUP_End";
        break;
      case 0x8080:
        cmdstring = "ACP_FIRMUP2";
        break;
      case 0x8090:
        cmdstring = "ACP_INFO_HDD";
        break;
      case 0x80A0:
        switch (getSpecialCmd(buf)) {
          // ACP_Special - details in packetbuf [32]
          case 0x01:
            cmdstring = "SPECIAL_CMD_REBOOT";
            break;
          case 0x02:
            cmdstring = "SPECIAL_CMD_SHUTDOWN";
            break;
          case 0x03:
            cmdstring = "SPECIAL_CMD_EMMODE";
            break;
          case 0x04:
            cmdstring = "SPECIAL_CMD_NORMMODE";
            break;
          case 0x05:
            cmdstring = "SPECIAL_CMD_BLINKLED";
            break;
          case 0x06:
            cmdstring = "SPECIAL_CMD_SAVECONFIG";
            break;
          case 0x07:
            cmdstring = "SPECIAL_CMD_LOADCONFIG";
            break;
          case 0x08:
            cmdstring = "SPECIAL_CMD_FACTORYSETUP";
            break;
          case 0x09:
            cmdstring = "SPECIAL_CMD_LIBLOCKSTATE";
            break;
          case 0x0a:
            cmdstring = "SPECIAL_CMD_LIBLOCK";
            break;
          case 0x0b:
            cmdstring = "SPECIAL_CMD_LIBUNLOCK";
            break;
          case 0x0c:
            cmdstring = "SPECIAL_CMD_AUTHENICATE";
            break;
          case 0x0d:
            cmdstring = "SPECIAL_CMD_EN_ONECMD";
            break;
          case 0x0e:
            cmdstring = "SPECIAL_CMD_DEBUGMODE";
            break;
          case 0x0f:
            cmdstring = "SPECIAL_CMD_MAC_EEPROM";
            break;
          case 0x12:
            cmdstring = "SPECIAL_CMD_MUULTILANG";
            break;
          default:
            cmdstring = "Unknown SPECIAL_CMD";
            break;
        }
        break;
      case 0x80D0:
        cmdstring = "ACP_PART";
        break;
      case 0x80E0:
        cmdstring = "ACP_INFO_RAID";
        break;
      case 0x8A10:
        cmdstring = "ACP_CMD";
        break;
      case 0x8B10:
        cmdstring = "ACP_FILE_SEND";
        break;
      case 0x8B20:
        cmdstring = "ACP_FILESEND_END";
        break;
      case 0x8E00:
        cmdstring = "ACP_Discover";
        break;

                                  // Answers to ACP-Commands
                                  // Currently missing, but defined in clientUtil_server:
                                  //     ACP_FORMAT_Reply
                                  //     ACP_ERASE_USER_Reply
      case 0xC020:
        cmdstring = "ACP_Discover_Reply";
        break;
      case 0xC030:
        cmdstring = "ACP_Change_IP_Reply";
        break;
      case 0xC040:
        cmdstring = "ACP_Ping_Reply";
        break;
      case 0xC050:
        cmdstring = "ACP_Info_Reply";
        break;
      case 0xC070:
        cmdstring = "ACP_FIRMUP_End_Reply";
        break;
      case 0xC080:
        cmdstring = "ACP_FIRMUP2_Reply";
        break;
      case 0xC090:
        cmdstring = "ACP_INFO_HDD_Reply";
        break;
      case 0xC0A0:
        cmdstring = "ACP_Special_Reply";
        break;
                                       // further handling possible. - necessary?
      case 0xC0D0:
        cmdstring = "ACP_PART_Reply";
        break;
      case 0xC0E0:
        cmdstring = "ACP_INFO_RAID_Reply";
        break;
      case 0xCA10:
        cmdstring = "ACP_CMD_Reply";
        break;
      case 0xCB10:
        cmdstring = "ACP_FILE_SEND_Reply";
        break;
      case 0xCB20:
        cmdstring = "ACP_FILESEND_END_Reply";
        break;
                                            // Unknown! - Error?
      default:
        cmdstring = "Unknown ACP command - possible error!";
    }
    return cmdstring;
  }

  // retreive ErrorCode out of receive buffer
  private int getErrorCode(byte[] buf) {
    return (int) (buf[28] & 0xFF) + (int) ((buf[29] & 0xFF) << 8)
          +  (int) ((buf[30] & 0xFF) << 16) + (int) ((buf[31] & 0xFF) << 24);
  }


  // Translate ErrorCode to meaningful string
  private String getErrorMsg(byte[] buf) {
    String acpStatus = bufferToHex(buf, 31, 1) + bufferToHex(buf, 30, 1)
               + bufferToHex(buf, 29, 1) + bufferToHex(buf, 28, 1);
    int ErrorCode = getErrorCode(buf);

    String ErrorString;
    switch (ErrorCode) {
      // There should be an error state ACP_OK, TODO: Test
      case 0x00000000:
        ErrorString = "ACP_STATE_OK";
        break;
      case 0x80000000:
        ErrorString = "ACP_STATE_MALLOC_ERROR";
        break;
      case 0x80000001:
        ErrorString = "ACP_STATE_PASSWORD_ERROR";
        break;
      case 0x80000002:
        ErrorString = "ACP_STATE_NO_CHANGE";
        break;
      case 0x80000003:
        ErrorString = "ACP_STATE_MODE_ERROR";
        break;
      case 0x80000004:
        ErrorString = "ACP_STATE_CRC_ERROR";
        break;
      case 0x80000005:
        ErrorString = "ACP_STATE_NOKEY";
        break;
      case 0x80000006:
        ErrorString = "ACP_STATE_DIFFMODEL";
        break;
      case 0x80000007:
        ErrorString = "ACP_STATE_NOMODEM";
        break;
      case 0x80000008:
        ErrorString = "ACP_STATE_COMMAND_ERROR";
        break;
      case 0x80000009:
        ErrorString = "ACP_STATE_NOT_UPDATE";
        break;
      case 0x8000000A:
        ErrorString = "ACP_STATE_PERMIT_ERROR";
        break;
      case 0x8000000B:
        ErrorString = "ACP_STATE_OPEN_ERROR";
        break;
      case 0x8000000C:
        ErrorString = "ACP_STATE_READ_ERROR";
        break;
      case 0x8000000D:
        ErrorString = "ACP_STATE_WRITE_ERROR";
        break;
      case 0x8000000E:
        ErrorString = "ACP_STATE_COMPARE_ERROR";
        break;
      case 0x8000000F:
        ErrorString = "ACP_STATE_MOUNT_ERROR";
        break;
      case 0x80000010:
        ErrorString = "ACP_STATE_PID_ERROR";
        break;
      case 0x80000011:
        ErrorString = "ACP_STATE_FIRM_TYPE_ERROR";
        break;
      case 0x80000012:
        ErrorString = "ACP_STATE_FORK_ERROR";
        break;
      case 0xFFFFFFFF:
        ErrorString = "ACP_STATE_FAILURE";
        break;
      // unknown error, better use ErrorCode and format it to hex
      default:
        ErrorString = "ACP_STATE_UNKNOWN_ERROR (" + acpStatus + ")";
    }
    return ErrorString;
  }

  /**
     * setacpheader
     * Helper function. Creates an ACP header in the given buf.
     *
     * @param buf byte[]        buffer for packet data
     * @param acpcmd String     HexString (2 byte) with ACPCommand
     * @param connid String     HexString (6 byte) with Connection ID
     * @param targetmac String  HexString (6 byte) with targets MAC
     * @param payloadsize byte  Length of payload following header
     *              (for ACPSpecial command this is fixed to 0x28 byte!)
     */
  private void setacpheader(byte[] buf, String acpcmd, String connid,
                  String targetmac, byte payloadsize) {
    buf[0] = 0x20; // length of header, 32 bytes
    buf[4] = 0x08; // minor packet version
    buf[6] = 0x01; // major packet version
    buf[8] = hextobyte(acpcmd.substring(2, 4))[0]; // lowbyte of ACP command
    buf[9] = hextobyte(acpcmd.substring(0, 2))[0]; // highbyte of ACP command
    buf[10] = payloadsize;

    byte[] test = hextobyte(connid);
    System.arraycopy(test, 0, buf, 16, 6);
    System.arraycopy(hextobyte(targetmac), 0, buf, 22, 6);
  }

  // creates an ACPReboot packet, ACP_EN_ONECMD protected
  private byte[] getacpreboot(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x01; // type ACPReboot

    return (buf);
  }

  // creates an ACPShutdown packet, ACP_EN_ONECMD protected
  private byte[] getacpshutdown(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x02; // type ACPShutdown

    return (buf);
  }

  // creates an ACPEMMode packet, ACP_EN_ONECMD protected
  private byte[] getacpEMMode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x03; // type ACPEMMode

    return (buf);
  }

  // creates an ACPnormmode packet, ACP_EN_ONECMD protected
  private byte[] getacpnormmode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x04; // type ACPNormmode

    return (buf);
  }

  // creates an ACPblinkled packet, also plays a series of tones
  private byte[] getacpblinkled(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x05; // type ACPBlinkled

    return (buf);
  }

  // creates an ACPsaveconfig packet
  private byte[] getacpsaveconfig(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x06; // type ACPsaveconfig

    return (buf);
  }

  // creates an ACPloadconfig packet
  private byte[] getacploadconfig(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x07; // type ACPloadconfig

    return (buf);
  }

  // creates an ACPenonecmd packet with the encrypted password (HexString 8 byte)
  private byte[] getacpenonecmd(String connid, String targetmac,
                  byte[] password) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) 0x28);
    buf[32] = 0x0d;

    System.arraycopy(password, 0, buf, 40, 8);
    return (buf);
  }

  // creates an ACPDebugmode packet
  // unclear what this causes on the LS
  private byte[] getacpdebugmode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x0e; // type ACPDebugmode

    return (buf);
  }

  // creates an ACPMultilang packet, ACP_EN_ONECMD protected
  // Used for setting GUI language, then additional parameter for language is needed
  private byte[] getacpmultilang(String connid, String targetmac,
                   byte Language) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x12; // type ACPMultilang

    buf[0x24] = Language; // seems to be a 4 byte value, starting at 0x24

    return (buf);
  }

  // creates an ACPDiscover packet
  // LS answers with a packet giving firmware details and a key used for pw encryption
  private byte[] getacpdisc(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "8020", connid, targetmac, (byte) 0x28);

    return (buf);
  }

  //newer version of discovery packet required by some devs
  private byte[] getacpdisc2(String connid, String targetmac) {
    byte[] buf = new byte[32];
    setacpheader(buf, "8E00", connid, targetmac, (byte) 0x00);

    return (buf);
  }

  // creates an ACPchangeip packet
  private byte[] getacpchangeip(String connid, String targetmac, byte[] newip,
                  byte[] newMask, boolean usedhcp,
                  byte[] encPassword) {
    byte[] buf = new byte[144];
    setacpheader(buf, "8030", connid, targetmac, (byte) 112);

    System.arraycopy(encPassword, 0, buf, 0x40, encPassword.length);
    // actually 144 byte long, contains password


    if (usedhcp) {
      buf[0x2C] = (byte) 1; // could be: DHCP=true - seems always to be true,
      // expect DHCP and password beyond 0x38
    }
    for (int i = 0; i <= 3; i++) {
      buf[0x33 - i] = newip[i]; // ip starts at 0x30, low byte first
      buf[0x37 - i] = newMask[i]; // mask starts at 0x34, low byte first
    }

    return (buf);
  }

  // create a correct ACPAuth packet
  private byte[] getacpauth(String connid, String targetmac,
                  byte[] password) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) 0x28);
    buf[32] = 0x0c;

    System.arraycopy(password, 0, buf, 40, password.length);
    return (buf);
  }


  // creates an ACPCMD packet, used to send telnet-style commands to the LS
  private byte[] getacpcmd(String connid, String targetmac, String cmd) {
    if (cmd.length() > 210) {
      outError("Command line too long (>210 chars).");
    }

    byte[] buf = new byte[cmd.length() + 44];
    setacpheader(buf, "8a10", connid, targetmac, (byte) (cmd.length() + 12));
    buf[32] = (byte) (cmd.length());
    buf[36] = 0x03; // type

    System.arraycopy(cmd.getBytes(defaultCharset), 0, buf, 40, cmd.length());

    return (buf);
  }

  public byte[] encryptacppassword(String _password, byte[] _key) {
    if (_password.length() > 24) {
      outError("The acp_commander only allows password lengths up to 24 chars");
    }
    if (_password.length() == 0) {
      return new byte[8];
    }

    byte[] sub_passwd = new byte[8];
    int sub_length = 0;
    byte[] result = new byte[(_password.length() + 7 >> 3) * 8];

    for (int i = 0; i < (_password.length() + 7) >> 3; i++) {
      sub_length = _password.length() - i * 8;
      if (sub_length > 8) {
        sub_length = 8;
      }

      System.arraycopy(_password.substring(i * 8).getBytes(defaultCharset), 0,
                 sub_passwd, 0, sub_length);
      if (sub_length < 8) {
        sub_passwd[sub_length] = (byte) 0x00; // end of string must be 0x00
      }

      System.arraycopy(encacppassword(sub_passwd, _key), 0, result, i * 8,
                 8);
    }

    return result;
  }

  private byte[] encacppassword(byte[] _password, byte[] _key) {
    //
    // mimmicks route from LSUpdater.exe, starting at 0x00401700
    // key is a 4 byte array (changed order, key 6ae2ad78 => (0x6a, 0xe2, 0xad, 0x78)
    // password = ap_servd, key= 6ae2ad78 gives encrypted 19:A4:F7:9B:AF:7B:C4:DD
    //
    byte[] new_key = new byte[8];
    byte[] result = new byte[8];

    // first generate initial encryption key (new_key) from key
    for (int i = 0; i < 4; i++) {
      new_key[3 - i] = (byte) (_key[i]); // lower 4 bytes
      new_key[4 + i] = (byte) ((_key[i] ^ _key[3 - i]) * _key[3 - i]); // higher 4 bytes
    }
    // use new_key to generate scrambled (xor) password, new_key is regularly altered
    int j = 0;
    int n;
    for (int i = 0; i < 4; i++) {
      // encryption of first char, first alter new_key
      new_key[0] = (byte) (_password[j] ^ new_key[0]);

      n = 2;
      for (int k = 0; k < i; k++) { // only executed if i > 1
        new_key[n] = (byte) (new_key[n] ^ new_key[n - 2]);
        n = n + 2;
      }

      result[i] = new_key[j];

      // above is repeated (more or less) for 2nd char, first alter new_key
      new_key[1] = (byte) (_password[j + 1] ^ new_key[1]);

      n = 3;
      for (int k = 0; k < i; k++) { // only executed if i > 1
        new_key[n] = (byte) (new_key[n] ^ new_key[n - 2]);
        n = n + 2;
      }

      result[7 - i] = new_key[j + 1];
      j = j + 2;
    }

    return (result);
  }


  private void rcvacpHexDump(byte[] buf) {
    // very simple hex | char debug output of received packet for debugging
    try {
      byte onebyte;

      System.out.println("Buffer-Length: " + buf.length);
      for (int j = 0; j < (buf.length / 16); j++) {
        if (j == 0) {
          System.out.println("ACP-Header:");
        }
        if (j == 2) {
          System.out.println("ACP-Payload:");
        }

        System.out.print(j * 16 + "::\t");
        for (int i = 0; i <= 15; i++) {
          System.out.print(bufferToHex(buf, j * 16 + i, 1) + " ");
        }
        System.out.print("\t");

        for (int i = 0; i <= 15; i++) {
          onebyte = buf[j * 16 + i];
          if ((onebyte != 0x0A) & (onebyte != 0x09)) {
            System.out.print((char) onebyte);
          } else {
            System.out.print(" ");
          }
        }
        System.out.println("");
      }
    } catch (java.lang.ArrayIndexOutOfBoundsException ArrayE) {
      outError(ArrayE.toString());
    }
  }

  /* Analyse ACPDisc answer packet, get hostname, hostIP, DHCP-state, FW-version
     * outACPrcvDisc(byte[] buf, int _debug)
     *  INPUT
     *    buf      ... byte [], buffer with received data
     *    _debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACPdiscovery reply" = packet type
     *            1 - formatted output
     *            2 - host name
     *            3 - IP
     *            4 - MAC
     *            5 - Product string
     *            6 - Product ID
     *            7 - FW version
     *            8 - key (used for pwd encryption in regular authentication process)
     */
  private String[] rcvacpDisc(byte[] buf, int _debug) {
    String[] result = new String[9];
    int _pckttype = 0;
    int _out = 1;
    int _hostname = 2;
    int _ip = 3;
    int _mac = 4;
    int _productstr = 5;
    int _productid = 6;
    int _FWversion = 7;
    int _key = 8;

    for (int i = 0; i < result.length; i++) {
      result[i] = "";
    }

    result[_pckttype] = "ACPdiscovery reply";
    try {
      // get IP
      byte[] targetip = new byte[4];
      for (int i = 0; i <= 3; i++) {
        targetip[i] = buf[35 - i];
      }
      InetAddress targetAddr = InetAddress.getByAddress(targetip);
      result[_ip] = targetAddr.toString();

      // get host name
      int i = 48;
      while ((buf[i] != 0x00) & (i < buf.length)) {
        result[_hostname] = result[_hostname] + (char) buf[i++];
      }

      // Product ID string starts at byte 80
      i = 80;
      while ((buf[i] != 0x00) & (i < buf.length)) {
        result[_productstr] = result[_productstr] + (char) buf[i++];
      }

      // Product ID starts at byte 192 low to high
      for (i = 3; i >= 0; i--) {
        result[_productid] = result[_productid] + buf[192 + i];
      }

      // MAC starts at byte 311
      for (i = 0; i <= 5; i++) {
        result[_mac] = result[_mac] + bufferToHex(buf, i + 311, 1);
        if (i != 5) {
          result[_mac] = result[_mac] + ":";
        }
      }

      // Key - changes with connectionid (everytime) -> key to password encryption?
      for (i = 0; i <= 3; i++) {
        result[_key] = result[_key] + bufferToHex(buf, 47 - i, 1);
      }

      // Firmware version starts at 187
      result[_FWversion] = buf[187] + buf[188] + "." + buf[189] + buf[190];

      result[_out] = (result[_hostname] + "\t"
                + result[_ip].replace("/","") + "\t"
                + String.format("%-" + 20 + "s", result[_productstr]) + "\t"
                + "ID=" + result[_productid] + "\t"
                + "mac: " + result[_mac] + "\t"
                + "FW=  " + result[_FWversion] + "\t"
               //+ "Key=" + result[_key] + "\t"
               );
    } catch (java.net.UnknownHostException unkhoste) {
      outError(unkhoste.getMessage());
    }
    return (result);
  }

  /* Analyses incoming ACP Replys - TODO progress, still needs better handling
     *  rcvacp(byte[] buf, int _debug)
     *  INPUT
     *    buf      ... byte [], buffer with received data
     *    _debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACP... reply" = packet type
     *            1 - formatted output
     *             2..n - possible details (ACPdiscovery)
     */
  private String[] rcvacp(byte[] buf, int debug) {
    if (debug >= 3) {
      rcvacpHexDump(buf);
    }

    String[] result;
    String acpReply;
    int acptype = 0;
    String acpStatus;

    // get type of ACP answer both as long and hexstring
    acptype = (buf[8] & 0xFF) + (buf[9] & 0xFF) * 256; // &0xFF necessary to avoid neg. values
    acpReply = bufferToHex(buf, 9, 1) + bufferToHex(buf, 8, 1);

    //@georg check!
    // value = 0xFFFFFFFF if ERROR occured
    acpStatus = bufferToHex(buf, 31, 1) + bufferToHex(buf, 30, 1)
          + bufferToHex(buf, 29, 1) + bufferToHex(buf, 28, 1);
    if (acpStatus.equalsIgnoreCase("FFFFFFFF")) {
      outDebug("Received packet (" + acpReply + ") has the error-flag set!\n"
          + "For 'authenticate' that is (usually) OK as we do send a buggy packet.", 1);
    }

    switch (acptype) {
      case 0xc020: // ACP discovery
        outDebug("received ACP Discovery reply", 1);
        result = rcvacpDisc(buf, debug);
        break;
      case 0xc030: // ACP changeIP
        outDebug("received ACP change IP reply", 1);
        result = new String[2]; //handling needed ?
        result[0] = "ACP change IP reply";
        result[1] = getErrorMsg(buf);
        break;
      case 0xc0a0: // ACP special command
        outDebug("received ACP special command reply", 1);
        result = new String[2]; //handling needed ?
        result[0] = "ACP special command reply";
        result[1] = getErrorMsg(buf);

        //            result[1] = "OK"; // should be set according to acpStatus!
        break;
      case 0xca10: // ACPcmd
        outDebug("received ACPcmd reply", 1);

        result = new String[2];
        result[0] = "ACPcmd reply";
        result[1] = "";
        int index = 40;
        while ((buf[index] != 0x00) & (index < buf.length)) {
          result[1] = result[1] + (char) buf[index++];
        }

        // filter the LSPro default answere "**no message**" as it led to some user queries/worries
        if (result[1].equalsIgnoreCase("**no message**")) {
          result[1] = "OK (" + getErrorMsg(buf) + ")";
        }
        break;
      case 0xce00: // ACP discovery
        outDebug("received ACP Discovery reply", 1);
        result = rcvacpDisc(buf, debug);
        break;
      default:
        result = new String[2]; //handling needed ?
        result[0] = "Unknown ACP-Reply packet: 0x" + acpReply;
        result[1] = "Unknown ACP-Reply packet: 0x" + acpReply; // add correct status!
    }
    outDebug("ACP analysis result: " + result[1], 2);
    return (result);
  }

  //
  // Standard warning, explanation functions
  //

  private void outInfoTimeout() {
    System.out.println(
            "A SocketTimeoutException usually indicates bad firewall settings.\n"
            + "Check especially for *UDP* port " + Port.toString()
            + " and make sure that the connection to your LS is working.");
    if (Port.intValue() != 22936) {
      outWarning("The Timeout could also be caused as you specified "
                + "(parameter -p) to use port " + Port.toString()
                + " which differs from standard port 22936.");
    }
  }

  private void outInfoSocket() {
    System.out.println(
            "A SocketException often indicates bad firewall settings.\n"
            + "The acp_commander / your java enviroment needs to send/recevie on UDP port "
            + Port.toString() + ".");
  }

  private void outInfoSetTarget() {
    System.out.println(
        "A UnknownHostException usually indicates that the specified target is not known "
        + "to your PC (can not be resolved).\n"
        + "Possible reasons are typos in the target parameter \"-t\", connection or "
        + "name resolution problems.\n"
        + "Also make sure that the target - here your Linkstation / Terastation - is powered on.");
  }

  //
  // Helper functions, should be moved to own classes
  //

  private void outDebug(String message, int debuglevel) {
    // negative debuglevels are considered as errors!
    if (debuglevel < 0) {
      outError(message);
      return;
    }

    if (debuglevel <= getdebuglevel()) {
      System.out.println(message);
    }
  }

  private void outError(String message) {
    System.err.println("ERROR: " + message);
    System.exit( -1);
  }

  private void outWarning(String message) {
    System.out.println("WARNING: " + message);
  }

  private byte[] hextobyte(String hexstr) {
    String pureHex = hexstr.replaceAll(":", "");
    byte[] bts = new byte[pureHex.length() / 2];
    for (int i = 0; i < bts.length; i++) {
      bts[i] = (byte) Integer.parseInt(pureHex.substring(2 * i, 2 * i + 2),16);
    }
    return (bts);
  }

  public static String bufferToHex(byte[] buffer, int startOffset, int length) {
    StringBuilder sb = new StringBuilder(length * 2);

    for (int i = startOffset; i < (startOffset + length); i++) {
      sb.append(String.format("%02x", buffer[i]));
    }
    return sb.toString().toUpperCase();
  }
}
