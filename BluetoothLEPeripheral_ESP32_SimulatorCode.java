function HandleService() {

///////////////////////////////////////////////
//// Local variables declaration

var mPod = 0x7b22737369645072696d223a224d484332222c2270775072696d223a2274657265736131393633222c2273736964536563223a224d4843222c227077536563223a2274657265736131393633227d;
var mConfig = "{"ssidPrim":"MHC","pwPrim":"teresa1963","ssidSec":"MHC2","pwSec":"teresa1963"}";
var mRunning = false;
///////////////////////////////////////////////
//// Handler's interface

  var inst = {
    /**
    * The global object, this object is available for every custom handlers.
    */
    mGlobalObject: {},

    /* This function will be called at initialization */
    initialize: initialize,

    /* Build response data */
    buildReadResponseData: buildReadResponseData,

    /* Build notify data */
    buildNotifyResponseData: buildNotifyResponseData,

    /* Handle onRead request */
    onWriteRequest: onWriteRequest,

    /* Handle onSubscribe */
    onSubscribed: onSubscribed,

    /* Handle onUnSubscribe */
    onUnSubscribed: onUnSubscribed,

    onConnectionLost: onConnectionLost,
  };

  return inst;


  /**
  * Initialization
  */
  function initialize() {
    // A console log will log a message into WEB CONSOLE (use adb logcat)
    console.log('[my-service] Initialized.');
  }

  /* Finalization */
  function finalize() {
    console.log('[my-service] Finalize.');
  }

  /**
  * Return array of bytes that will be used to response to client on it's ReadRequest.
  *
  * @returns {[]} bytes array
  */
  function buildReadResponseData() {
    console.log('[my-service] buildReadResponseData: ' + mConfig);
    return [mConfig];
  }

  /**
  *
  * @returns {*[]}
  */
  function buildNotifyResponseData() {
    console.log('[my-service] buildNotifyResponseData.');
    return buildReadResponseData();
  }

  /**
  *
  * @param bytes
  */
  function onWriteRequest(bytes) {
    console.log('[my-service] onWriteRequest: ' +bytes);
    inst.mGlobalObject.logger.info('[my-service] onWriteRequest: ' +bytes);
    if(bytes && bytes.length == 16) { // erase
      mConfig = 0x7b22737369645072696d223a22222c2270775072696d223a22222c2273736964536563223a22222c227077536563223a22227d;
//    } else if(bytes && bytes.length == 15) { // read
//      mPod = 0x7b22737369645072696d223a224d484332222c2270775072696d223a2274657265736131393633222c2273736964536563223a224d4843222c227077536563223a2274657265736131393633227d;
    } else if(bytes && bytes.length > 30) {
      mConfig = bytes[0];
    }

    // You can call to function notify of the mGlobalObject to
    // notify to subscribed devices. Bytes to be written will be read from
    // buildNotifyResponseData() function.

    // Please use inst.__characteristicId to identify this handler.
    // This id is injected into the inst at it was initialized.

    inst.mGlobalObject.notify(inst.__characteristicId);
  }

  function onSubscribed() {
    console.log('[my-service] onSubscribed. Start updating data.');
    inst.mGlobalObject.logger.info('[my-service] onSubscribed. Start updating data.');
    mRunning = true;
    updateData();
  }

  function onUnSubscribed() {
    console.log('[my-service] onUnSubscribed. Stop updating data.');

    // You can use the logger object of mGlobalObject to write logs to the screen
    inst.mGlobalObject.logger.info('[my-service] onUnSubscribed. Stop updating data.');
    mRunning = false;
  }

  function onConnectionLost() {
    mRunning = false;
    console.log('[my-service] - CONNECTION LOST.');
    inst.mGlobalObject.logger.info('[my-service] - CONNECTION LOST.');
  }

  function updateData() {
    if(!mRunning) {
      return;
    }
    mPod ++;
    console.log('[my-service] Data updated: ' +mPod);

    setTimeout(updateData, 3000);
  }

}
