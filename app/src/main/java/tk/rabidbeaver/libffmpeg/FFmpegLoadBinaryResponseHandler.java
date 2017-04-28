package tk.rabidbeaver.libffmpeg;

interface FFmpegLoadBinaryResponseHandler extends ResponseHandler {

    /**
     * on Fail
     */
    void onFailure();

    /**
     * on Success
     */
    void onSuccess();

}
