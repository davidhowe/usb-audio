#!/usr/bin/env python

"""
Python emulation of the Android Java USB Host API.

Currently only the synchronous API is emulated. The Java API is described here:

http://developer.android.com/guide/topics/connectivity/usb/host.html

This emulation is built on top of a pure Python wrapper for the cross platform
USB access library libusb-1.0. The wrapper is an open source project located here:

https://github.com/vpelletier/python-libusb1
"""

import usb1

__all__ = [
  'Context', 'UsbManager', 'USB_DIR_IN', 'USB_DIR_OUT',
  'UsbDeviceConnection', 'UsbDevice',
  'UsbConfiguration', 'UsbInterface', 'UsbEndpoint',
]

"""
Defines USB constants that correspond to definitions in linux/usb/ch9.h of the Linux kernel.
"""
USB_DIR_IN = 0x80
USB_DIR_OUT = 0x00

class Context(object):
  """Interface to global information about an application environment.

  This is an abstract class whose implementation is provided by the Android system.
  It allows access to application-specific resources and classes, as well as up-calls
  for application-level operations such as launching activities, broadcasting and
  receiving intents, etc.
  """
  USB_SERVICE = 0

  @staticmethod
  def getSystemService(unused):
    return UsbManager()

class UsbManager(object):
  """
  This class allows you to access the state of USB and communicate with USB devices.
  Currently only host mode is supported in the public API.
  You can obtain an instance of this class by calling Context.getSystemService().
  """
  def __init__(self):
    self.context = usb1.USBContext()

  def getDeviceList(self):
    return [UsbDevice(d) for d in self.context.getDeviceList(skip_on_access_error=True)]

  def openDevice(self, device):
    try:
      hdl = device.device.open()
      return UsbDeviceConnection(hdl)
    except:
      return None

class UsbDeviceConnection(object):
  """
  This class is used for sending and receiving data and control messages to a USB device.
  Instances of this class are created by openDevice(UsbDevice).
  """
  def __init__(self, hdl):
    self.hdl = hdl

  # int bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int length, int timeout)
  # Performs a bulk transaction on the given endpoint.
  def bulkTransfer(self, ep, buffer, length, timeout):
    try:
      if ep.getDirection() == usb1.ENDPOINT_IN:
        data = self.hdl.bulkRead(ep.getAddress(), length, timeout)
        buffer[:len(data)] = data
        return len(data)
      else:
        return self.hdl.bulkWrite(ep.getAddress(), buffer[:length], timeout)
    except usb1.USBError as e:
      return e.value

int
bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int offset, int length, int timeout)

Performs a bulk transaction on the given endpoint.

  # boolean claimInterface(UsbInterface intf, boolean force)
  # Claims exclusive access to a UsbInterface.
  def claimInterface(self, intf, force):
    try:
      self.hdl.claimInterface(intf.getId())
      return True
    except:
      return False

  # void close()
  # Releases all system resources related to the device.
  def close(self):
    self.hdl.close()

  # int controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout)
  # Performs a control transaction on endpoint zero for this device.
  def controlTransfer(self, type, request, value, index, buffer, length, timeout):
    try:
      if type & 0x80:
        data = self.hdl.controlRead(type, request, value, index, length, timeout)
        buffer[:len(data)] = data
        return len(data)
      else:
        return self.hdl.controlWrite(type, request, value, index, buffer, timeout)
    except usb1.USBError as e:
      return e.value

int
controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int offset, int length, int timeout)

Performs a control transaction on endpoint zero for this device.


int
getFileDescriptor()

Returns the native file descriptor for the device, or -1 if the device is not opened.
byte[]
getRawDescriptors()

Returns the raw USB descriptors for the device.

  # String getSerial()
  # Returns the serial number for the device.
  def getSerial(self):
    dev = self.hdl.getDevice()
    return self.hdl.getASCIIStringDescriptor(dev.device_descriptor.iSerialNumber)

  # boolean releaseInterface(UsbInterface intf)
  # Releases exclusive access to a UsbInterface.
  def releaseInterface(self, intf):
    try:
      self.hdl.releaseInterface(intf.getId())
      return True
    except:
      return False

UsbRequest
requestWait()

Waits for the result of a queue(ByteBuffer, int) operation Note that this may return requests queued on multiple UsbEndpoints.

  # boolean setConfiguration(UsbConfiguration configuration)
  # Sets the device's current UsbConfiguration.
  def setConfiguration(self, configuration):
    try:
      self.hdl.setConfiguration(configuration.getId())
      return True
    except:
      return False

  # boolean setInterface(UsbInterface intf)
  # Sets the current UsbInterface.
  def setInterface(self, intf):
    try:
      self.hdl.setInterfaceAltSetting(intf.getId(), intf.getAlternateSetting())
      return True
    except:
      return False

class UsbDevice(object):
  """
  This class represents a USB device attached to the android device with the
  android device acting as the USB host. Each device contains one or more
  UsbInterfaces, each of which contains a number of UsbEndpoints (the channels
  via which data is transmitted over USB).

  This class contains information (along with UsbInterface and UsbEndpoint) that
  describes the capabilities of the USB device. To communicate with the device,
  you open a UsbDeviceConnection for the device and use UsbRequest to send and
  receive data on an endpoint. controlTransfer(int, int, int, int, byte[], int, int)
  is used for control requests on endpoint zero.
  """
  def __init__(self, device):
    self.device = device
    self.config = None

  def _getConfig(self):
    if not self.config:
      # TODO: Get the active configuration instead of assuming 0
      self.config = self.getConfiguration(0)
    return self.config

  # UsbConfiguration getConfiguration(int index)
  # Returns the UsbConfiguration at the given index.
  def getConfiguration(self, index):
    if len(self.device):
      return UsbConfiguration(self.device[index])
    else:
      return None

  # int getConfigurationCount()
  # Returns the number of UsbConfigurations this device contains.
  def getConfigurationCount(self):
    return self.device.getNumConfigurations()

  # int getDeviceClass()
  # Returns the devices's class field.
  def getDeviceClass(self):
    return self.device.getDeviceClass()

  # int getDeviceId()
  # Returns a unique integer ID for the device.
  def getDeviceId(self):
    return self.device.getBusNumber()*1000 + self.device.getDeviceAddress()

  # String getDeviceName()
  # Returns the name of the device.
  def getDeviceName(self):
    return '/dev/bus/usb/%03i/%03i' % (
      self.device.getBusNumber(),
      self.device.getDeviceAddress()
    )

  # int getDeviceProtocol()
  # Returns the device's protocol field.
  def getDeviceProtocol(self):
    return self.device.getDeviceProtocol()

  # int getDeviceSubclass()
  # Returns the device's subclass field.
  def getDeviceSubclass(self):
    return self.device.getDeviceSubClass()

  # UsbInterface getInterface(int index)
  # Returns the UsbInterface at the given index.
  def getInterface(self, index):
    config = self._getConfig()
    if config:
      return config.getInterface(index)
    else:
      return None

  # int getInterfaceCount()
  # Returns the number of UsbInterfaces this device contains.
  def getInterfaceCount(self):
    config = self._getConfig()
    if config:
      return config.getInterfaceCount()
    else:
      return 0

  # String getManufacturerName()
  # Returns the manufacturer name of the device.
  def getManufacturerName(self):
    try:
      return self.device.getManufacturer()
    except:
      return None

  # int getProductId()
  # Returns a product ID for the device.
  def getProductId(self):
    return self.device.getProductID()

  # String getProductName()
  # Returns the product name of the device.
  def getProductName(self):
    try:
      return self.device.getProduct()
    except:
      return None

  # String getSerialNumber()
  # Returns the serial number of the device.
  def getSerialNumber(self):
    try:
      return self.device.getSerialNumber()
    except:
      return None

  # int getVendorId()
  # Returns a vendor ID for the device.
  def getVendorId(self):
    return self.device.getVendorID()

  # String getVersion()
  # Returns the version number of the device.
  def getVersion(self):
    return self.device.getbcdDevice()

static int
getDeviceId(String name)

static String
getDeviceName(int id)

boolean
equals(Object o)

Compares this instance with the specified object and indicates if they are equal.
int
hashCode()

Returns an integer hash code for this object.
String
toString()

Returns a string containing a concise, human-readable description of this object.

class UsbConfiguration(object):
  """
  A class representing a configuration on a UsbDevice. A USB configuration can
  have one or more interfaces, each one providing a different piece of functionality,
  separate from the other interfaces. An interface will have one or more UsbEndpoints,
  which are the channels by which the host transfers data with the device.
  """
  def __init__(self, cfg):
    self.cfg = cfg
    self.itfs = []
    for itf in self.cfg:
      for iset in itf:
        self.itfs.append(UsbInterface(iset))

  # int getId()
  # Returns the configuration's ID field.
  def getId(self):
    return self.cfg.getConfigurationValue()

  # UsbInterface getInterface(int index)
  # Returns the UsbInterface at the given index.
  def getInterface(self, index):
    return self.itfs[index]

  # int getInterfaceCount()
  # Returns the number of UsbInterfaces this configuration contains.
  def getInterfaceCount(self):
    return len(self.itfs)

  # int getMaxPower()
  # Returns the configuration's max power consumption, in milliamps.
  def getMaxPower(self):
    return self.cfg.getMaxPower()

  # boolean isRemoteWakeup()
  # Returns the remote-wakeup attribute value configuration's attributes field.
  def isRemoteWakeup(self):
    return bool(self.cfg.getAttributes() & 0x20)

  # boolean isSelfPowered()
  # Returns the self-powered attribute value configuration's attributes field.
  def isSelfPowered(self):
    return bool(self.cfg.getAttributes() & 0x40)

String
getName()

Returns the configuration's name.
String
toString()

Returns a string containing a concise, human-readable description of this object.

class UsbInterface(object):
  """
  A class representing an interface on a UsbDevice. USB devices can have one or
  more interfaces, each one providing a different piece of functionality, separate
  from the other interfaces. An interface will have one or more UsbEndpoints, which
  are the channels by which the host transfers data with the device.
  """
  def __init__(self, iset):
    self.iset = iset

  # int getAlternateSetting()
  # Returns the interface's bAlternateSetting field.
  def getAlternateSetting(self):
    return self.iset.getAlternateSetting()

  # UsbEndpoint getEndpoint(int index)
  # Returns the UsbEndpoint at the given index.
  def getEndpoint(self, index):
    return UsbEndpoint(self.iset[index])

  # int getEndpointCount()
  # Returns the number of UsbEndpoints this interface contains.
  def getEndpointCount(self):
    return self.iset.getNumEndpoints()

  # int getId()
  # Returns the interface's bInterfaceNumber field.
  def getId(self):
    return self.iset.getNumber()

  # int getInterfaceClass()
  # Returns the interface's class field.
  def getInterfaceClass(self):
    return self.iset.getClass()

  # int getInterfaceProtocol()
  # Returns the interface's protocol field.
  def getInterfaceProtocol(self):
    return self.iset.getProtocol()

  # int getInterfaceSubclass()
  # Returns the interface's subclass field.
  def getInterfaceSubclass(self):
    return self.iset.getSubClass()

String
getName()

Returns the interface's name.
String
toString()

Returns a string containing a concise, human-readable description of this object.

class UsbEndpoint(object):
  """
  A class representing an endpoint on a UsbInterface. Endpoints are the channels
  for sending and receiving data over USB. Typically bulk endpoints are used for
  sending non-trivial amounts of data. Interrupt endpoints are used for sending
  small amounts of data, typically events, separately from the main data streams.
  The endpoint zero is a special endpoint for control messages sent from the host
  to device. Isochronous endpoints are currently unsupported.
  """
  def __init__(self, ep):
    self.ep = ep

  # int getAddress()
  # Returns the endpoint's address field.
  def getAddress(self):
    return self.ep.getAddress()

  # int getAttributes()
  # Returns the endpoint's attributes field.
  def getAttributes(self):
    return self.ep.getAttributes()

  # int getDirection()
  # Returns the endpoint's direction.
  def getDirection(self):
    return self.ep.getAddress() & usb1.ENDPOINT_DIR_MASK

  # int getEndpointNumber()
  # Extracts the endpoint's endpoint number from its address
  def getEndpointNumber(self):
    return self.ep.getAddress() & usb1.ENDPOINT_ADDRESS_MASK

  # int getInterval()
  # Returns the endpoint's interval field.
  def getInterval(self):
    return self.ep.getInterval()

  # int getMaxPacketSize()
  # Returns the endpoint's maximum packet size.
  def getMaxPacketSize(self):
    return self.ep.getMaxPacketSize()

  # int getType()
  # Returns the endpoint's type.
  def getType(self):
    return self.ep.getAttributes() & 0x03

String
toString()

Returns a string containing a concise, human-readable description of this object.

def dump_UsbDevice(obj, verbose=False):
  print('\nUsbDevice[', obj.getDeviceId(), ']')
  # print('.getDeviceId():', obj.getDeviceId())
  print('.getDeviceName():', obj.getDeviceName())
  print('.getDeviceClass():', obj.getDeviceClass())
  print('.getDeviceSubclass():', obj.getDeviceSubclass())
  print('.getDeviceProtocol():', obj.getDeviceProtocol())
  print('.getVendorId():', hex(obj.getVendorId()))
  print('.getProductId():', hex(obj.getProductId()))
  print('.getVersion():', hex(obj.getVersion()))
  print('.getManufacturerName():', obj.getManufacturerName())
  print('.getProductName():', obj.getProductName())
  print('.getSerialNumber():', obj.getSerialNumber())
  print('.getConfigurationCount():', obj.getConfigurationCount())
  print('.getInterfaceCount():', obj.getInterfaceCount())
  # print('.getInterface(0):', obj.getInterface(0))
  cfg = obj.getConfiguration(0)
  if cfg and verbose:
    dump_UsbConfiguration(cfg, True)

def dump_UsbConfiguration(obj, verbose=False):
  print('\nUsbConfiguration[', obj.getId(), ']')
  # print('.getId():', obj.getId())
  print('.getMaxPower():', obj.getMaxPower())
  print('.isRemoteWakeup():', obj.isRemoteWakeup())
  print('.isSelfPowered():', obj.isSelfPowered())
  print('.getInterfaceCount():', obj.getInterfaceCount())
  if verbose:
    for i in range(obj.getInterfaceCount()):
      dump_UsbInterface(obj.getInterface(i), True)

def dump_UsbInterface(obj, verbose=False):
  print('\nUsbInterface[', obj.getId(), ':', obj.getAlternateSetting(), ']')
  # print('.getId():', obj.getId())
  # print('.getAlternateSetting():', obj.getAlternateSetting())
  print('.getInterfaceClass():', obj.getInterfaceClass())
  print('.getInterfaceSubclass():', obj.getInterfaceSubclass())
  print('.getInterfaceProtocol():', obj.getInterfaceProtocol())
  print('.getEndpointCount():', obj.getEndpointCount())
  if verbose:
    for i in range(obj.getEndpointCount()):
      dump_UsbEndpoint(obj.getEndpoint(i))

def dump_UsbEndpoint(obj):
  print('\nUsbEndpoint[', obj.getEndpointNumber(), ']')
  print('.getAddress():', hex(obj.getAddress()))
  print('.getAttributes():', obj.getAttributes())
  print('.getDirection():', obj.getDirection())
  # print('.getEndpointNumber():', obj.getEndpointNumber())
  print('.getInterval():', obj.getInterval())
  print('.getMaxPacketSize():', obj.getMaxPacketSize())
  print('.getType():', obj.getType())

if __name__ == '__main__':
  manager = Context.getSystemService(Context.USB_SERVICE)
  for dev in manager.getDeviceList():
    dump_UsbDevice(dev, True)
