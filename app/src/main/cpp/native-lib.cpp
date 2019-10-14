#include <jni.h>
#include <string>
#include "opencv2/opencv.hpp"
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/calib3d/calib3d.hpp>
#include <cstddef>
using namespace cv;

extern "C" JNIEXPORT void
Java_com_example_ndktest2_MainActivity_StereomMatching(JNIEnv *env,jobject, jint width, jint height, jbyteArray yuv) {
    Mat undisort_img;
    Mat dispaty_data;
    Mat disparty_map;
    double min, max;
    jbyte *bytes;
    jint a;

    int disp = 32;
    int minDisparity = 0;
    int numDisparities = 32;
    int SADWindowSize = 3;//3
    int P1 = 0;
    int P2 = 0;
    int disp12MaxDiff = 0;
    int preFilterCap = 0;
    int uniquenessRatio = 0;
    int speckleWindowSize = 0;
    int speckleRange = 0;
    bool fullDp = false;

    jbyte* _yuv  = env->GetByteArrayElements(yuv, 0);
    Mat myuv(height + height/2, width, CV_8UC1, (unsigned char *)_yuv);
    Mat mgray(height, width, CV_8UC1, (unsigned char *)_yuv);
    fastNlMeansDenoising(mgray, undisort_img);
    normalize(undisort_img, undisort_img, 0, 255, NORM_MINMAX, -1, Mat());

    Ptr<StereoSGBM> sgbm = StereoSGBM::create(0,6,11);
    sgbm->setPreFilterCap(32);
    sgbm->setBlockSize(SADWindowSize);
    int cn = undisort_img.channels();
    sgbm->setP1(8 * cn*SADWindowSize*SADWindowSize);
    sgbm->setP2(32 * cn*SADWindowSize*SADWindowSize);
    sgbm->setMinDisparity(0);
    sgbm->setNumDisparities(numDisparities);
    sgbm->setUniquenessRatio(10);
    sgbm->setSpeckleWindowSize(100);
    sgbm->setSpeckleRange(32);
    sgbm->setDisp12MaxDiff(1);
    sgbm->setMode(StereoSGBM::MODE_SGBM);
    sgbm->compute(undisort_img, undisort_img, dispaty_data);
    minMaxLoc(dispaty_data, &min, &max);
    dispaty_data.convertTo(disparty_map,CV_8UC1,255/(max-min),-255*min/(max-min));

    return env->ReleaseByteArrayElements(yuv, _yuv, 0);;
}
