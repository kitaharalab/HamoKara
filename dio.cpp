/*****************************************************************
// Copyright 2014-2015 Masanori Morise. All Rights Reserved.
// Author: mmorise [at] yamanashi.ac.jp (Masanori Morise)
//
// F0 estimation based on DIO(Distributed Inline-filter Operation)
// Referring to World(http://ml.cs.yamanashi.ac.jp/world/index.html).
*****************************************************************/
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <sys/time.h>
#include <termios.h>
#include <unistd.h>

#ifndef ECHOPRT
	#define ECHOPRT ECHOE
#endif

#define FRAMEPERIOD 5.0
// Commands for FFT (This is the same as FFTW)
#define FFT_FORWARD 1
#define FFT_BACKWARD 2
#define FFT_ESTIMATE 3

#ifndef WORLD_CONSTANT_NUMBERS_H_
	#define WORLD_CONSTANT_NUMBERS_H_

	#ifndef DWORD
		#define DWORD uint32_t
	#endif
	DWORD timeGetTime() {
		struct timeval tv;
		gettimeofday(&tv, NULL);
		DWORD ret = static_cast<DWORD>(tv.tv_usec / 1000 + tv.tv_sec * 1000);
		return ret;
	}
	namespace world {
		const double kPi = 3.1415926535897932384;
		const double kMySafeGuardMinimum = 0.000000000001;
		const double kFloorF0 = 71.0;
		const double kCeilF0 = 800.0;
		const double kDefaultF0 = 500.0;
		const double kDefaultF0ForSynthesis = 150.0;
		const double kLog2 = 0.69314718055994529;
		// Maximum standard deviation not to be selected as a best f0.
		const double kMaximumValue = 100000.0;
	}  // namespace world
#endif
// Complex number for FFT
typedef double fft_complex[2];
// Struct used for FFT
typedef struct {
	int n;
	int sign;
	unsigned int flags;
	fft_complex *c_in;
	double *in;
	fft_complex *c_out;
	double *out;
	double *input;
	int *ip;
	double *w;
} fft_plan;

// Forward FFT in the real sequence
typedef struct {
	int fft_size;
	double *waveform;
	fft_complex *spectrum;
	fft_plan forward_fft;
} ForwardRealFFT;

// Inverse FFT in the real sequence
typedef struct {
	int fft_size;
	double *waveform;
	fft_complex *spectrum;
	fft_plan inverse_fft;
} InverseRealFFT;

// Minimum phase analysis from logarithmic power spectrum
typedef struct {
	int fft_size;
	double *log_spectrum;
	fft_complex *minimum_phase_spectrum;
	fft_complex *cepstrum;
	fft_plan inverse_fft;
	fft_plan forward_fft;
} MinimumPhaseAnalysis;

typedef struct {
	double f0_floor;
	double f0_ceil;
	double channels_in_octave;
	double frame_period;  // msec
	int speed;  // (1, 2, ..., 12)
} DioOption;

typedef struct {
  double *negative_interval_locations;
  double *negative_intervals;
  int number_of_negatives;
  double *positive_interval_locations;
  double *positive_intervals;
  int number_of_positives;
  double *peak_interval_locations;
  double *peak_intervals;
  int number_of_peaks;
  double *dip_interval_locations;
  double *dip_intervals;
  int number_of_dips;
} ZeroCrossings;

/***********************************************************/
extern "C" int GetSamplesForDIO(int fs, int x_length, double frame_period) {
	return static_cast<int>(x_length / static_cast<double>(fs) / (frame_period / 1000.0)) + 1;
}
extern "C" int GetFFTSizeForCheapTrick(int fs) {
	return static_cast<int>(pow(2.0, 1.0 + static_cast<int>(log(3.0 * fs / world::kFloorF0 + 1) / world::kLog2)));
}
extern "C" int getch() {
	int ch;
	struct termios oldt, newt;

	tcgetattr(STDIN_FILENO, &oldt);
	memcpy(&newt, &oldt, sizeof(newt));
	newt.c_lflag &=
	~(ECHO | ICANON | ECHOE | ECHOK | ECHONL | ECHOPRT | ECHOKE | ICRNL);
	tcsetattr(STDIN_FILENO, TCSANOW, &newt);
	ch = getchar();
	tcsetattr(STDIN_FILENO, TCSANOW, &oldt);

	return ch;
}
inline int MyMax(int x, int y) {
  return x > y ? x : y;
}
inline double MyMax(double x, double y) {
  return x > y ? x : y;
}
inline int MyMin(int x, int y) {
  return x < y ? x : y;
}
inline double MyMin(double x, double y) {
  return x < y ? x : y;
}
extern "C" int GetSuitableFFTSize(int sample) {
  return static_cast<int>(pow(2.0, static_cast<int>(log(static_cast<double>(sample)) / world::kLog2) + 1.0));
}
extern "C" int matlab_round(double x) {
  return x > 0 ? static_cast<int>(x + 0.5) : static_cast<int>(x - 0.5);
}
//-----------------------------------------------------------------------------
// CheckEvent() returns 1, provided that the input value is over 1.
// This function is for RawEventByDio().
//-----------------------------------------------------------------------------
inline int CheckEvent(int x) {
  return x > 0 ? 1 : 0;
}
extern "C" void fft_destroy_plan(fft_plan p) {
	p.n = 0;
	p.in = NULL;
	p.c_in = NULL;
	p.out = NULL;
	p.c_out = NULL;
	p.sign = 0;
	p.flags = 0;
	delete[] p.input;
	delete[] p.ip;
	delete[] p.w;
}
extern "C" void FilterForDecimate(double *x, int x_length, int r, double *y) {
	double a[3], b[2];  // filter Coefficients
	switch (r) {
		case 11:  // fs : 44100 (default)
		a[0] = 2.450743295230728;
		a[1] = -2.06794904601978;
		a[2] = 0.59574774438332101;
		b[0] = 0.0026822508007163792;
		b[1] = 0.0080467524021491377;
		break;
		case 12:  // fs : 48000
		a[0] = 2.4981398605924205;
		a[1] = -2.1368928194784025;
		a[2] = 0.62187513816221485;
		b[0] = 0.0021097275904709001;
		b[1] = 0.0063291827714127002;
		break;
		case 10:
		a[0] = 2.3936475118069387;
		a[1] = -1.9873904075111861;
		a[2] = 0.5658879979027055;
		b[0] = 0.0034818622251927556;
		b[1] = 0.010445586675578267;
		break;
		case 9:
		a[0] = 2.3236003491759578;
		a[1] = -1.8921545617463598;
		a[2] = 0.53148928133729068;
		b[0] = 0.0046331164041389372;
		b[1] = 0.013899349212416812;
		break;
		case 8:  // fs : 32000
		a[0] = 2.2357462340187593;
		a[1] = -1.7780899984041358;
		a[2] = 0.49152555365968692;
		b[0] = 0.0063522763407111993;
		b[1] = 0.019056829022133598;
		break;
		case 7:
		a[0] = 2.1225239019534703;
		a[1] = -1.6395144861046302;
		a[2] = 0.44469707800587366;
		b[0] = 0.0090366882681608418;
		b[1] = 0.027110064804482525;
		break;
		case 6:  // fs : 24000 and 22050
		a[0] = 1.9715352749512141;
		a[1] = -1.4686795689225347;
		a[2] = 0.3893908434965701;
		b[0] = 0.013469181309343825;
		b[1] = 0.040407543928031475;
		break;
		case 5:
		a[0] = 1.7610939654280557;
		a[1] = -1.2554914843859768;
		a[2] = 0.3237186507788215;
		b[0] = 0.021334858522387423;
		b[1] = 0.06400457556716227;
		break;
		case 4:  // fs : 16000
		a[0] = 1.4499664446880227;
		a[1] = -0.98943497080950582;
		a[2] = 0.24578252340690215;
		b[0] = 0.036710750339322612;
		b[1] = 0.11013225101796784;
		break;
		case 3:
		a[0] = 0.95039378983237421;
		a[1] = -0.67429146741526791;
		a[2] = 0.15412211621346475;
		b[0] = 0.071221945171178636;
		b[1] = 0.21366583551353591;
		break;
		case 2:  // fs : 8000
		a[0] = 0.041156734567757189;
		a[1] = -0.42599112459189636;
		a[2] = 0.041037215479961225;
		b[0] = 0.16797464681802227;
		b[1] = 0.50392394045406674;
		break;
		default:
		a[0] = 0.0;
		a[1] = 0.0;
		a[2] = 0.0;
		b[0] = 0.0;
		b[1] = 0.0;
	}
	// Filtering on time domain.
	double w[3] = {0.0, 0.0, 0.0};
	double wt;
	for (int i = 0; i < x_length; ++i) {
		wt = x[i] + a[0] * w[0] + a[1] * w[1] + a[2] * w[2];
		y[i] = b[0] * wt + b[1] * w[0] + b[1] * w[1] + b[0] * w[2];
		w[2] = w[1];
		w[1] = w[0];
		w[0] = wt;
	}
}
extern "C" void makeipt(int nw, int *ip) {
	int j, l, m, m2, p, q;

	ip[2] = 0;
	ip[3] = 16;
	m = 2;
	for (l = nw; l > 32; l >>= 2) {
		m2 = m << 1;
		q = m2 << 3;
		for (j = m; j < m2; j++) {
			p = ip[j] << 2;
			ip[m + j] = p;
			ip[m2 + j] = p + q;
		}
		m = m2;
	}
}
extern "C" void makect(int nc, int *ip, double *c) {
	int j, nch;
	double delta;

	ip[1] = nc;
	if (nc > 1) {
		nch = nc >> 1;
		delta = atan(1.0) / nch;
		c[0] = cos(delta * nch);
		c[nch] = 0.5 * c[0];
		for (j = 1; j < nch; j++) {
			c[j] = 0.5 * cos(delta * j);
			c[nc - j] = 0.5 * sin(delta * j);
		}
	}
}
extern "C" void dstsub(int n, double *a, int nc, double *c) {
  int j, k, kk, ks, m;
  double wkr, wki, xr;

  m = n >> 1;
  ks = nc / n;
  kk = 0;
  for (j = 1; j < m; j++) {
    k = n - j;
    kk += ks;
    wkr = c[kk] - c[nc - kk];
    wki = c[kk] + c[nc - kk];
    xr = wki * a[k] - wkr * a[j];
    a[k] = wkr * a[k] + wki * a[j];
    a[j] = xr;
  }
  a[m] *= c[0];
}
extern "C" void dctsub(int n, double *a, int nc, double *c) {
  int j, k, kk, ks, m;
  double wkr, wki, xr;
  m = n >> 1;
  ks = nc / n;
  kk = 0;
  for (j = 1; j < m; j++) {
    k = n - j;
    kk += ks;
    wkr = c[kk] - c[nc - kk];
    wki = c[kk] + c[nc - kk];
    xr = wki * a[j] - wkr * a[k];
    a[j] = wkr * a[j] + wki * a[k];
    a[k] = xr;
  }
  a[m] *= c[0];
}
extern "C" void rftbsub(int n, double *a, int nc, double *c) {
  int j, k, kk, ks, m;
  double wkr, wki, xr, xi, yr, yi;
  m = n >> 1;
  ks = 2 * nc / m;
  kk = 0;
  for (j = 2; j < m; j += 2) {
    k = n - j;
    kk += ks;
    wkr = 0.5 - c[nc - kk];
    wki = c[kk];
    xr = a[j] - a[k];
    xi = a[j + 1] + a[k + 1];
    yr = wkr * xr + wki * xi;
    yi = wkr * xi - wki * xr;
    a[j] -= yr;
    a[j + 1] -= yi;
    a[k] += yr;
    a[k + 1] -= yi;
  }
}
extern "C" void rftfsub(int n, double *a, int nc, double *c) {
  int j, k, kk, ks, m;
  double wkr, wki, xr, xi, yr, yi;
  m = n >> 1;
  ks = 2 * nc / m;
  kk = 0;
  for (j = 2; j < m; j += 2) {
    k = n - j;
    kk += ks;
    wkr = 0.5 - c[nc - kk];
    wki = c[kk];
    xr = a[j] - a[k];
    xi = a[j + 1] + a[k + 1];
    yr = wkr * xr - wki * xi;
    yi = wkr * xi + wki * xr;
    a[j] -= yr;
    a[j + 1] -= yi;
    a[k] += yr;
    a[k + 1] -= yi;
  }
}
extern "C" void cftx020(double *a) {
  double x0r, x0i;
  x0r = a[0] - a[2];
  x0i = a[1] - a[3];
  a[0] += a[2];
  a[1] += a[3];
  a[2] = x0r;
  a[3] = x0i;
}
extern "C" void cftb040(double *a) {
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
  x0r = a[0] + a[4];
  x0i = a[1] + a[5];
  x1r = a[0] - a[4];
  x1i = a[1] - a[5];
  x2r = a[2] + a[6];
  x2i = a[3] + a[7];
  x3r = a[2] - a[6];
  x3i = a[3] - a[7];
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[2] = x1r + x3i;
  a[3] = x1i - x3r;
  a[4] = x0r - x2r;
  a[5] = x0i - x2i;
  a[6] = x1r - x3i;
  a[7] = x1i + x3r;
}
extern "C" void cftf040(double *a) {
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
  x0r = a[0] + a[4];
  x0i = a[1] + a[5];
  x1r = a[0] - a[4];
  x1i = a[1] - a[5];
  x2r = a[2] + a[6];
  x2i = a[3] + a[7];
  x3r = a[2] - a[6];
  x3i = a[3] - a[7];
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[2] = x1r - x3i;
  a[3] = x1i + x3r;
  a[4] = x0r - x2r;
  a[5] = x0i - x2i;
  a[6] = x1r + x3i;
  a[7] = x1i - x3r;
}
extern "C" void cftf082(double *a, double *w) {
  double wn4r, wk1r, wk1i, x0r, x0i, x1r, x1i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i,
    y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;
  wn4r = w[1];
  wk1r = w[2];
  wk1i = w[3];
  y0r = a[0] - a[9];
  y0i = a[1] + a[8];
  y1r = a[0] + a[9];
  y1i = a[1] - a[8];
  x0r = a[4] - a[13];
  x0i = a[5] + a[12];
  y2r = wn4r * (x0r - x0i);
  y2i = wn4r * (x0i + x0r);
  x0r = a[4] + a[13];
  x0i = a[5] - a[12];
  y3r = wn4r * (x0r - x0i);
  y3i = wn4r * (x0i + x0r);
  x0r = a[2] - a[11];
  x0i = a[3] + a[10];
  y4r = wk1r * x0r - wk1i * x0i;
  y4i = wk1r * x0i + wk1i * x0r;
  x0r = a[2] + a[11];
  x0i = a[3] - a[10];
  y5r = wk1i * x0r - wk1r * x0i;
  y5i = wk1i * x0i + wk1r * x0r;
  x0r = a[6] - a[15];
  x0i = a[7] + a[14];
  y6r = wk1i * x0r - wk1r * x0i;
  y6i = wk1i * x0i + wk1r * x0r;
  x0r = a[6] + a[15];
  x0i = a[7] - a[14];
  y7r = wk1r * x0r - wk1i * x0i;
  y7i = wk1r * x0i + wk1i * x0r;
  x0r = y0r + y2r;
  x0i = y0i + y2i;
  x1r = y4r + y6r;
  x1i = y4i + y6i;
  a[0] = x0r + x1r;
  a[1] = x0i + x1i;
  a[2] = x0r - x1r;
  a[3] = x0i - x1i;
  x0r = y0r - y2r;
  x0i = y0i - y2i;
  x1r = y4r - y6r;
  x1i = y4i - y6i;
  a[4] = x0r - x1i;
  a[5] = x0i + x1r;
  a[6] = x0r + x1i;
  a[7] = x0i - x1r;
  x0r = y1r - y3i;
  x0i = y1i + y3r;
  x1r = y5r - y7r;
  x1i = y5i - y7i;
  a[8] = x0r + x1r;
  a[9] = x0i + x1i;
  a[10] = x0r - x1r;
  a[11] = x0i - x1i;
  x0r = y1r + y3i;
  x0i = y1i - y3r;
  x1r = y5r + y7r;
  x1i = y5i + y7i;
  a[12] = x0r - x1i;
  a[13] = x0i + x1r;
  a[14] = x0r + x1i;
  a[15] = x0i - x1r;
}
extern "C" void cftf081(double *a, double *w) {
  double wn4r, x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i,
    y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;

  wn4r = w[1];
  x0r = a[0] + a[8];
  x0i = a[1] + a[9];
  x1r = a[0] - a[8];
  x1i = a[1] - a[9];
  x2r = a[4] + a[12];
  x2i = a[5] + a[13];
  x3r = a[4] - a[12];
  x3i = a[5] - a[13];
  y0r = x0r + x2r;
  y0i = x0i + x2i;
  y2r = x0r - x2r;
  y2i = x0i - x2i;
  y1r = x1r - x3i;
  y1i = x1i + x3r;
  y3r = x1r + x3i;
  y3i = x1i - x3r;
  x0r = a[2] + a[10];
  x0i = a[3] + a[11];
  x1r = a[2] - a[10];
  x1i = a[3] - a[11];
  x2r = a[6] + a[14];
  x2i = a[7] + a[15];
  x3r = a[6] - a[14];
  x3i = a[7] - a[15];
  y4r = x0r + x2r;
  y4i = x0i + x2i;
  y6r = x0r - x2r;
  y6i = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  x2r = x1r + x3i;
  x2i = x1i - x3r;
  y5r = wn4r * (x0r - x0i);
  y5i = wn4r * (x0r + x0i);
  y7r = wn4r * (x2r - x2i);
  y7i = wn4r * (x2r + x2i);
  a[8] = y1r + y5r;
  a[9] = y1i + y5i;
  a[10] = y1r - y5r;
  a[11] = y1i - y5i;
  a[12] = y3r - y7i;
  a[13] = y3i + y7r;
  a[14] = y3r + y7i;
  a[15] = y3i - y7r;
  a[0] = y0r + y4r;
  a[1] = y0i + y4i;
  a[2] = y0r - y4r;
  a[3] = y0i - y4i;
  a[4] = y2r - y6i;
  a[5] = y2i + y6r;
  a[6] = y2r + y6i;
  a[7] = y2i - y6r;
}
extern "C" void cftf162(double *a, double *w) {
  double wn4r, wk1r, wk1i, wk2r, wk2i, wk3r, wk3i,
    x0r, x0i, x1r, x1i, x2r, x2i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i,
    y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i,
    y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i,
    y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

  wn4r = w[1];
  wk1r = w[4];
  wk1i = w[5];
  wk3r = w[6];
  wk3i = -w[7];
  wk2r = w[8];
  wk2i = w[9];
  x1r = a[0] - a[17];
  x1i = a[1] + a[16];
  x0r = a[8] - a[25];
  x0i = a[9] + a[24];
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  y0r = x1r + x2r;
  y0i = x1i + x2i;
  y4r = x1r - x2r;
  y4i = x1i - x2i;
  x1r = a[0] + a[17];
  x1i = a[1] - a[16];
  x0r = a[8] + a[25];
  x0i = a[9] - a[24];
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  y8r = x1r - x2i;
  y8i = x1i + x2r;
  y12r = x1r + x2i;
  y12i = x1i - x2r;
  x0r = a[2] - a[19];
  x0i = a[3] + a[18];
  x1r = wk1r * x0r - wk1i * x0i;
  x1i = wk1r * x0i + wk1i * x0r;
  x0r = a[10] - a[27];
  x0i = a[11] + a[26];
  x2r = wk3i * x0r - wk3r * x0i;
  x2i = wk3i * x0i + wk3r * x0r;
  y1r = x1r + x2r;
  y1i = x1i + x2i;
  y5r = x1r - x2r;
  y5i = x1i - x2i;
  x0r = a[2] + a[19];
  x0i = a[3] - a[18];
  x1r = wk3r * x0r - wk3i * x0i;
  x1i = wk3r * x0i + wk3i * x0r;
  x0r = a[10] + a[27];
  x0i = a[11] - a[26];
  x2r = wk1r * x0r + wk1i * x0i;
  x2i = wk1r * x0i - wk1i * x0r;
  y9r = x1r - x2r;
  y9i = x1i - x2i;
  y13r = x1r + x2r;
  y13i = x1i + x2i;
  x0r = a[4] - a[21];
  x0i = a[5] + a[20];
  x1r = wk2r * x0r - wk2i * x0i;
  x1i = wk2r * x0i + wk2i * x0r;
  x0r = a[12] - a[29];
  x0i = a[13] + a[28];
  x2r = wk2i * x0r - wk2r * x0i;
  x2i = wk2i * x0i + wk2r * x0r;
  y2r = x1r + x2r;
  y2i = x1i + x2i;
  y6r = x1r - x2r;
  y6i = x1i - x2i;
  x0r = a[4] + a[21];
  x0i = a[5] - a[20];
  x1r = wk2i * x0r - wk2r * x0i;
  x1i = wk2i * x0i + wk2r * x0r;
  x0r = a[12] + a[29];
  x0i = a[13] - a[28];
  x2r = wk2r * x0r - wk2i * x0i;
  x2i = wk2r * x0i + wk2i * x0r;
  y10r = x1r - x2r;
  y10i = x1i - x2i;
  y14r = x1r + x2r;
  y14i = x1i + x2i;
  x0r = a[6] - a[23];
  x0i = a[7] + a[22];
  x1r = wk3r * x0r - wk3i * x0i;
  x1i = wk3r * x0i + wk3i * x0r;
  x0r = a[14] - a[31];
  x0i = a[15] + a[30];
  x2r = wk1i * x0r - wk1r * x0i;
  x2i = wk1i * x0i + wk1r * x0r;
  y3r = x1r + x2r;
  y3i = x1i + x2i;
  y7r = x1r - x2r;
  y7i = x1i - x2i;
  x0r = a[6] + a[23];
  x0i = a[7] - a[22];
  x1r = wk1i * x0r + wk1r * x0i;
  x1i = wk1i * x0i - wk1r * x0r;
  x0r = a[14] + a[31];
  x0i = a[15] - a[30];
  x2r = wk3i * x0r - wk3r * x0i;
  x2i = wk3i * x0i + wk3r * x0r;
  y11r = x1r + x2r;
  y11i = x1i + x2i;
  y15r = x1r - x2r;
  y15i = x1i - x2i;
  x1r = y0r + y2r;
  x1i = y0i + y2i;
  x2r = y1r + y3r;
  x2i = y1i + y3i;
  a[0] = x1r + x2r;
  a[1] = x1i + x2i;
  a[2] = x1r - x2r;
  a[3] = x1i - x2i;
  x1r = y0r - y2r;
  x1i = y0i - y2i;
  x2r = y1r - y3r;
  x2i = y1i - y3i;
  a[4] = x1r - x2i;
  a[5] = x1i + x2r;
  a[6] = x1r + x2i;
  a[7] = x1i - x2r;
  x1r = y4r - y6i;
  x1i = y4i + y6r;
  x0r = y5r - y7i;
  x0i = y5i + y7r;
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  a[8] = x1r + x2r;
  a[9] = x1i + x2i;
  a[10] = x1r - x2r;
  a[11] = x1i - x2i;
  x1r = y4r + y6i;
  x1i = y4i - y6r;
  x0r = y5r + y7i;
  x0i = y5i - y7r;
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  a[12] = x1r - x2i;
  a[13] = x1i + x2r;
  a[14] = x1r + x2i;
  a[15] = x1i - x2r;
  x1r = y8r + y10r;
  x1i = y8i + y10i;
  x2r = y9r - y11r;
  x2i = y9i - y11i;
  a[16] = x1r + x2r;
  a[17] = x1i + x2i;
  a[18] = x1r - x2r;
  a[19] = x1i - x2i;
  x1r = y8r - y10r;
  x1i = y8i - y10i;
  x2r = y9r + y11r;
  x2i = y9i + y11i;
  a[20] = x1r - x2i;
  a[21] = x1i + x2r;
  a[22] = x1r + x2i;
  a[23] = x1i - x2r;
  x1r = y12r - y14i;
  x1i = y12i + y14r;
  x0r = y13r + y15i;
  x0i = y13i - y15r;
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  a[24] = x1r + x2r;
  a[25] = x1i + x2i;
  a[26] = x1r - x2r;
  a[27] = x1i - x2i;
  x1r = y12r + y14i;
  x1i = y12i - y14r;
  x0r = y13r - y15i;
  x0i = y13i + y15r;
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  a[28] = x1r - x2i;
  a[29] = x1i + x2r;
  a[30] = x1r + x2i;
  a[31] = x1i - x2r;
}
extern "C" void cftf161(double *a, double *w) {
  double wn4r, wk1r, wk1i,
    x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i,
    y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i,
    y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i,
    y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

  wn4r = w[1];
  wk1r = w[2];
  wk1i = w[3];
  x0r = a[0] + a[16];
  x0i = a[1] + a[17];
  x1r = a[0] - a[16];
  x1i = a[1] - a[17];
  x2r = a[8] + a[24];
  x2i = a[9] + a[25];
  x3r = a[8] - a[24];
  x3i = a[9] - a[25];
  y0r = x0r + x2r;
  y0i = x0i + x2i;
  y4r = x0r - x2r;
  y4i = x0i - x2i;
  y8r = x1r - x3i;
  y8i = x1i + x3r;
  y12r = x1r + x3i;
  y12i = x1i - x3r;
  x0r = a[2] + a[18];
  x0i = a[3] + a[19];
  x1r = a[2] - a[18];
  x1i = a[3] - a[19];
  x2r = a[10] + a[26];
  x2i = a[11] + a[27];
  x3r = a[10] - a[26];
  x3i = a[11] - a[27];
  y1r = x0r + x2r;
  y1i = x0i + x2i;
  y5r = x0r - x2r;
  y5i = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  y9r = wk1r * x0r - wk1i * x0i;
  y9i = wk1r * x0i + wk1i * x0r;
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  y13r = wk1i * x0r - wk1r * x0i;
  y13i = wk1i * x0i + wk1r * x0r;
  x0r = a[4] + a[20];
  x0i = a[5] + a[21];
  x1r = a[4] - a[20];
  x1i = a[5] - a[21];
  x2r = a[12] + a[28];
  x2i = a[13] + a[29];
  x3r = a[12] - a[28];
  x3i = a[13] - a[29];
  y2r = x0r + x2r;
  y2i = x0i + x2i;
  y6r = x0r - x2r;
  y6i = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  y10r = wn4r * (x0r - x0i);
  y10i = wn4r * (x0i + x0r);
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  y14r = wn4r * (x0r + x0i);
  y14i = wn4r * (x0i - x0r);
  x0r = a[6] + a[22];
  x0i = a[7] + a[23];
  x1r = a[6] - a[22];
  x1i = a[7] - a[23];
  x2r = a[14] + a[30];
  x2i = a[15] + a[31];
  x3r = a[14] - a[30];
  x3i = a[15] - a[31];
  y3r = x0r + x2r;
  y3i = x0i + x2i;
  y7r = x0r - x2r;
  y7i = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  y11r = wk1i * x0r - wk1r * x0i;
  y11i = wk1i * x0i + wk1r * x0r;
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  y15r = wk1r * x0r - wk1i * x0i;
  y15i = wk1r * x0i + wk1i * x0r;
  x0r = y12r - y14r;
  x0i = y12i - y14i;
  x1r = y12r + y14r;
  x1i = y12i + y14i;
  x2r = y13r - y15r;
  x2i = y13i - y15i;
  x3r = y13r + y15r;
  x3i = y13i + y15i;
  a[24] = x0r + x2r;
  a[25] = x0i + x2i;
  a[26] = x0r - x2r;
  a[27] = x0i - x2i;
  a[28] = x1r - x3i;
  a[29] = x1i + x3r;
  a[30] = x1r + x3i;
  a[31] = x1i - x3r;
  x0r = y8r + y10r;
  x0i = y8i + y10i;
  x1r = y8r - y10r;
  x1i = y8i - y10i;
  x2r = y9r + y11r;
  x2i = y9i + y11i;
  x3r = y9r - y11r;
  x3i = y9i - y11i;
  a[16] = x0r + x2r;
  a[17] = x0i + x2i;
  a[18] = x0r - x2r;
  a[19] = x0i - x2i;
  a[20] = x1r - x3i;
  a[21] = x1i + x3r;
  a[22] = x1r + x3i;
  a[23] = x1i - x3r;
  x0r = y5r - y7i;
  x0i = y5i + y7r;
  x2r = wn4r * (x0r - x0i);
  x2i = wn4r * (x0i + x0r);
  x0r = y5r + y7i;
  x0i = y5i - y7r;
  x3r = wn4r * (x0r - x0i);
  x3i = wn4r * (x0i + x0r);
  x0r = y4r - y6i;
  x0i = y4i + y6r;
  x1r = y4r + y6i;
  x1i = y4i - y6r;
  a[8] = x0r + x2r;
  a[9] = x0i + x2i;
  a[10] = x0r - x2r;
  a[11] = x0i - x2i;
  a[12] = x1r - x3i;
  a[13] = x1i + x3r;
  a[14] = x1r + x3i;
  a[15] = x1i - x3r;
  x0r = y0r + y2r;
  x0i = y0i + y2i;
  x1r = y0r - y2r;
  x1i = y0i - y2i;
  x2r = y1r + y3r;
  x2i = y1i + y3i;
  x3r = y1r - y3r;
  x3i = y1i - y3i;
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[2] = x0r - x2r;
  a[3] = x0i - x2i;
  a[4] = x1r - x3i;
  a[5] = x1i + x3r;
  a[6] = x1r + x3i;
  a[7] = x1i - x3r;
}
extern "C" void cftmdl2(int n, double *a, double *w) {
  int j, j0, j1, j2, j3, k, kr, m, mh;
  double wn4r, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y2r, y2i;

  mh = n >> 3;
  m = 2 * mh;
  wn4r = w[1];
  j1 = m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[0] - a[j2 + 1];
  x0i = a[1] + a[j2];
  x1r = a[0] + a[j2 + 1];
  x1i = a[1] - a[j2];
  x2r = a[j1] - a[j3 + 1];
  x2i = a[j1 + 1] + a[j3];
  x3r = a[j1] + a[j3 + 1];
  x3i = a[j1 + 1] - a[j3];
  y0r = wn4r * (x2r - x2i);
  y0i = wn4r * (x2i + x2r);
  a[0] = x0r + y0r;
  a[1] = x0i + y0i;
  a[j1] = x0r - y0r;
  a[j1 + 1] = x0i - y0i;
  y0r = wn4r * (x3r - x3i);
  y0i = wn4r * (x3i + x3r);
  a[j2] = x1r - y0i;
  a[j2 + 1] = x1i + y0r;
  a[j3] = x1r + y0i;
  a[j3 + 1] = x1i - y0r;
  k = 0;
  kr = 2 * m;
  for (j = 2; j < mh; j += 2) {
    k += 4;
    wk1r = w[k];
    wk1i = w[k + 1];
    wk3r = w[k + 2];
    wk3i = w[k + 3];
    kr -= 4;
    wd1i = w[kr];
    wd1r = w[kr + 1];
    wd3i = w[kr + 2];
    wd3r = w[kr + 3];
    j1 = j + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j] - a[j2 + 1];
    x0i = a[j + 1] + a[j2];
    x1r = a[j] + a[j2 + 1];
    x1i = a[j + 1] - a[j2];
    x2r = a[j1] - a[j3 + 1];
    x2i = a[j1 + 1] + a[j3];
    x3r = a[j1] + a[j3 + 1];
    x3i = a[j1 + 1] - a[j3];
    y0r = wk1r * x0r - wk1i * x0i;
    y0i = wk1r * x0i + wk1i * x0r;
    y2r = wd1r * x2r - wd1i * x2i;
    y2i = wd1r * x2i + wd1i * x2r;
    a[j] = y0r + y2r;
    a[j + 1] = y0i + y2i;
    a[j1] = y0r - y2r;
    a[j1 + 1] = y0i - y2i;
    y0r = wk3r * x1r + wk3i * x1i;
    y0i = wk3r * x1i - wk3i * x1r;
    y2r = wd3r * x3r + wd3i * x3i;
    y2i = wd3r * x3i - wd3i * x3r;
    a[j2] = y0r + y2r;
    a[j2 + 1] = y0i + y2i;
    a[j3] = y0r - y2r;
    a[j3 + 1] = y0i - y2i;
    j0 = m - j;
    j1 = j0 + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j0] - a[j2 + 1];
    x0i = a[j0 + 1] + a[j2];
    x1r = a[j0] + a[j2 + 1];
    x1i = a[j0 + 1] - a[j2];
    x2r = a[j1] - a[j3 + 1];
    x2i = a[j1 + 1] + a[j3];
    x3r = a[j1] + a[j3 + 1];
    x3i = a[j1 + 1] - a[j3];
    y0r = wd1i * x0r - wd1r * x0i;
    y0i = wd1i * x0i + wd1r * x0r;
    y2r = wk1i * x2r - wk1r * x2i;
    y2i = wk1i * x2i + wk1r * x2r;
    a[j0] = y0r + y2r;
    a[j0 + 1] = y0i + y2i;
    a[j1] = y0r - y2r;
    a[j1 + 1] = y0i - y2i;
    y0r = wd3i * x1r + wd3r * x1i;
    y0i = wd3i * x1i - wd3r * x1r;
    y2r = wk3i * x3r + wk3r * x3i;
    y2i = wk3i * x3i - wk3r * x3r;
    a[j2] = y0r + y2r;
    a[j2 + 1] = y0i + y2i;
    a[j3] = y0r - y2r;
    a[j3 + 1] = y0i - y2i;
  }
  wk1r = w[m];
  wk1i = w[m + 1];
  j0 = mh;
  j1 = j0 + m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[j0] - a[j2 + 1];
  x0i = a[j0 + 1] + a[j2];
  x1r = a[j0] + a[j2 + 1];
  x1i = a[j0 + 1] - a[j2];
  x2r = a[j1] - a[j3 + 1];
  x2i = a[j1 + 1] + a[j3];
  x3r = a[j1] + a[j3 + 1];
  x3i = a[j1 + 1] - a[j3];
  y0r = wk1r * x0r - wk1i * x0i;
  y0i = wk1r * x0i + wk1i * x0r;
  y2r = wk1i * x2r - wk1r * x2i;
  y2i = wk1i * x2i + wk1r * x2r;
  a[j0] = y0r + y2r;
  a[j0 + 1] = y0i + y2i;
  a[j1] = y0r - y2r;
  a[j1 + 1] = y0i - y2i;
  y0r = wk1i * x1r - wk1r * x1i;
  y0i = wk1i * x1i + wk1r * x1r;
  y2r = wk1r * x3r - wk1i * x3i;
  y2i = wk1r * x3i + wk1i * x3r;
  a[j2] = y0r - y2r;
  a[j2 + 1] = y0i - y2i;
  a[j3] = y0r + y2r;
  a[j3 + 1] = y0i + y2i;
}
extern "C" void cftmdl1(int n, double *a, double *w) {
  int j, j0, j1, j2, j3, k, m, mh;
  double wn4r, wk1r, wk1i, wk3r, wk3i;
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  mh = n >> 3;
  m = 2 * mh;
  j1 = m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[0] + a[j2];
  x0i = a[1] + a[j2 + 1];
  x1r = a[0] - a[j2];
  x1i = a[1] - a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i - x2i;
  a[j2] = x1r - x3i;
  a[j2 + 1] = x1i + x3r;
  a[j3] = x1r + x3i;
  a[j3 + 1] = x1i - x3r;
  wn4r = w[1];
  k = 0;
  for (j = 2; j < mh; j += 2) {
    k += 4;
    wk1r = w[k];
    wk1i = w[k + 1];
    wk3r = w[k + 2];
    wk3i = w[k + 3];
    j1 = j + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j] + a[j2];
    x0i = a[j + 1] + a[j2 + 1];
    x1r = a[j] - a[j2];
    x1i = a[j + 1] - a[j2 + 1];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    a[j] = x0r + x2r;
    a[j + 1] = x0i + x2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i - x2i;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j2] = wk1r * x0r - wk1i * x0i;
    a[j2 + 1] = wk1r * x0i + wk1i * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j3] = wk3r * x0r + wk3i * x0i;
    a[j3 + 1] = wk3r * x0i - wk3i * x0r;
    j0 = m - j;
    j1 = j0 + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j0] + a[j2];
    x0i = a[j0 + 1] + a[j2 + 1];
    x1r = a[j0] - a[j2];
    x1i = a[j0 + 1] - a[j2 + 1];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    a[j0] = x0r + x2r;
    a[j0 + 1] = x0i + x2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i - x2i;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j2] = wk1i * x0r - wk1r * x0i;
    a[j2 + 1] = wk1i * x0i + wk1r * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j3] = wk3i * x0r + wk3r * x0i;
    a[j3 + 1] = wk3i * x0i - wk3r * x0r;
  }
  j0 = mh;
  j1 = j0 + m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[j0] + a[j2];
  x0i = a[j0 + 1] + a[j2 + 1];
  x1r = a[j0] - a[j2];
  x1i = a[j0 + 1] - a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[j0] = x0r + x2r;
  a[j0 + 1] = x0i + x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  a[j2] = wn4r * (x0r - x0i);
  a[j2 + 1] = wn4r * (x0i + x0r);
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  a[j3] = -wn4r * (x0r + x0i);
  a[j3 + 1] = -wn4r * (x0i - x0r);
}
extern "C" void cftb1st(int n, double *a, double *w) {
  int j, j0, j1, j2, j3, k, m, mh;
  double wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i,
    wd1r, wd1i, wd3r, wd3i;
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;

  mh = n >> 3;
  m = 2 * mh;
  j1 = m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[0] + a[j2];
  x0i = -a[1] - a[j2 + 1];
  x1r = a[0] - a[j2];
  x1i = -a[1] + a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[0] = x0r + x2r;
  a[1] = x0i - x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i + x2i;
  a[j2] = x1r + x3i;
  a[j2 + 1] = x1i + x3r;
  a[j3] = x1r - x3i;
  a[j3 + 1] = x1i - x3r;
  wn4r = w[1];
  csc1 = w[2];
  csc3 = w[3];
  wd1r = 1;
  wd1i = 0;
  wd3r = 1;
  wd3i = 0;
  k = 0;
  for (j = 2; j < mh - 2; j += 4) {
    k += 4;
    wk1r = csc1 * (wd1r + w[k]);
    wk1i = csc1 * (wd1i + w[k + 1]);
    wk3r = csc3 * (wd3r + w[k + 2]);
    wk3i = csc3 * (wd3i + w[k + 3]);
    wd1r = w[k];
    wd1i = w[k + 1];
    wd3r = w[k + 2];
    wd3i = w[k + 3];
    j1 = j + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j] + a[j2];
    x0i = -a[j + 1] - a[j2 + 1];
    x1r = a[j] - a[j2];
    x1i = -a[j + 1] + a[j2 + 1];
    y0r = a[j + 2] + a[j2 + 2];
    y0i = -a[j + 3] - a[j2 + 3];
    y1r = a[j + 2] - a[j2 + 2];
    y1i = -a[j + 3] + a[j2 + 3];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    y2r = a[j1 + 2] + a[j3 + 2];
    y2i = a[j1 + 3] + a[j3 + 3];
    y3r = a[j1 + 2] - a[j3 + 2];
    y3i = a[j1 + 3] - a[j3 + 3];
    a[j] = x0r + x2r;
    a[j + 1] = x0i - x2i;
    a[j + 2] = y0r + y2r;
    a[j + 3] = y0i - y2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i + x2i;
    a[j1 + 2] = y0r - y2r;
    a[j1 + 3] = y0i + y2i;
    x0r = x1r + x3i;
    x0i = x1i + x3r;
    a[j2] = wk1r * x0r - wk1i * x0i;
    a[j2 + 1] = wk1r * x0i + wk1i * x0r;
    x0r = y1r + y3i;
    x0i = y1i + y3r;
    a[j2 + 2] = wd1r * x0r - wd1i * x0i;
    a[j2 + 3] = wd1r * x0i + wd1i * x0r;
    x0r = x1r - x3i;
    x0i = x1i - x3r;
    a[j3] = wk3r * x0r + wk3i * x0i;
    a[j3 + 1] = wk3r * x0i - wk3i * x0r;
    x0r = y1r - y3i;
    x0i = y1i - y3r;
    a[j3 + 2] = wd3r * x0r + wd3i * x0i;
    a[j3 + 3] = wd3r * x0i - wd3i * x0r;
    j0 = m - j;
    j1 = j0 + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j0] + a[j2];
    x0i = -a[j0 + 1] - a[j2 + 1];
    x1r = a[j0] - a[j2];
    x1i = -a[j0 + 1] + a[j2 + 1];
    y0r = a[j0 - 2] + a[j2 - 2];
    y0i = -a[j0 - 1] - a[j2 - 1];
    y1r = a[j0 - 2] - a[j2 - 2];
    y1i = -a[j0 - 1] + a[j2 - 1];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    y2r = a[j1 - 2] + a[j3 - 2];
    y2i = a[j1 - 1] + a[j3 - 1];
    y3r = a[j1 - 2] - a[j3 - 2];
    y3i = a[j1 - 1] - a[j3 - 1];
    a[j0] = x0r + x2r;
    a[j0 + 1] = x0i - x2i;
    a[j0 - 2] = y0r + y2r;
    a[j0 - 1] = y0i - y2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i + x2i;
    a[j1 - 2] = y0r - y2r;
    a[j1 - 1] = y0i + y2i;
    x0r = x1r + x3i;
    x0i = x1i + x3r;
    a[j2] = wk1i * x0r - wk1r * x0i;
    a[j2 + 1] = wk1i * x0i + wk1r * x0r;
    x0r = y1r + y3i;
    x0i = y1i + y3r;
    a[j2 - 2] = wd1i * x0r - wd1r * x0i;
    a[j2 - 1] = wd1i * x0i + wd1r * x0r;
    x0r = x1r - x3i;
    x0i = x1i - x3r;
    a[j3] = wk3i * x0r + wk3r * x0i;
    a[j3 + 1] = wk3i * x0i - wk3r * x0r;
    x0r = y1r - y3i;
    x0i = y1i - y3r;
    a[j3 - 2] = wd3i * x0r + wd3r * x0i;
    a[j3 - 1] = wd3i * x0i - wd3r * x0r;
  }
  wk1r = csc1 * (wd1r + wn4r);
  wk1i = csc1 * (wd1i + wn4r);
  wk3r = csc3 * (wd3r - wn4r);
  wk3i = csc3 * (wd3i - wn4r);
  j0 = mh;
  j1 = j0 + m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[j0 - 2] + a[j2 - 2];
  x0i = -a[j0 - 1] - a[j2 - 1];
  x1r = a[j0 - 2] - a[j2 - 2];
  x1i = -a[j0 - 1] + a[j2 - 1];
  x2r = a[j1 - 2] + a[j3 - 2];
  x2i = a[j1 - 1] + a[j3 - 1];
  x3r = a[j1 - 2] - a[j3 - 2];
  x3i = a[j1 - 1] - a[j3 - 1];
  a[j0 - 2] = x0r + x2r;
  a[j0 - 1] = x0i - x2i;
  a[j1 - 2] = x0r - x2r;
  a[j1 - 1] = x0i + x2i;
  x0r = x1r + x3i;
  x0i = x1i + x3r;
  a[j2 - 2] = wk1r * x0r - wk1i * x0i;
  a[j2 - 1] = wk1r * x0i + wk1i * x0r;
  x0r = x1r - x3i;
  x0i = x1i - x3r;
  a[j3 - 2] = wk3r * x0r + wk3i * x0i;
  a[j3 - 1] = wk3r * x0i - wk3i * x0r;
  x0r = a[j0] + a[j2];
  x0i = -a[j0 + 1] - a[j2 + 1];
  x1r = a[j0] - a[j2];
  x1i = -a[j0 + 1] + a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[j0] = x0r + x2r;
  a[j0 + 1] = x0i - x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i + x2i;
  x0r = x1r + x3i;
  x0i = x1i + x3r;
  a[j2] = wn4r * (x0r - x0i);
  a[j2 + 1] = wn4r * (x0i + x0r);
  x0r = x1r - x3i;
  x0i = x1i - x3r;
  a[j3] = -wn4r * (x0r + x0i);
  a[j3 + 1] = -wn4r * (x0i - x0r);
  x0r = a[j0 + 2] + a[j2 + 2];
  x0i = -a[j0 + 3] - a[j2 + 3];
  x1r = a[j0 + 2] - a[j2 + 2];
  x1i = -a[j0 + 3] + a[j2 + 3];
  x2r = a[j1 + 2] + a[j3 + 2];
  x2i = a[j1 + 3] + a[j3 + 3];
  x3r = a[j1 + 2] - a[j3 + 2];
  x3i = a[j1 + 3] - a[j3 + 3];
  a[j0 + 2] = x0r + x2r;
  a[j0 + 3] = x0i - x2i;
  a[j1 + 2] = x0r - x2r;
  a[j1 + 3] = x0i + x2i;
  x0r = x1r + x3i;
  x0i = x1i + x3r;
  a[j2 + 2] = wk1i * x0r - wk1r * x0i;
  a[j2 + 3] = wk1i * x0i + wk1r * x0r;
  x0r = x1r - x3i;
  x0i = x1i - x3r;
  a[j3 + 2] = wk3i * x0r + wk3r * x0i;
  a[j3 + 3] = wk3i * x0i - wk3r * x0r;
}
extern "C" void cftf1st(int n, double *a, double *w) {
  int j, j0, j1, j2, j3, k, m, mh;
  double wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i,
    wd1r, wd1i, wd3r, wd3i;
  double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i,
    y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;

  mh = n >> 3;
  m = 2 * mh;
  j1 = m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[0] + a[j2];
  x0i = a[1] + a[j2 + 1];
  x1r = a[0] - a[j2];
  x1i = a[1] - a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i - x2i;
  a[j2] = x1r - x3i;
  a[j2 + 1] = x1i + x3r;
  a[j3] = x1r + x3i;
  a[j3 + 1] = x1i - x3r;
  wn4r = w[1];
  csc1 = w[2];
  csc3 = w[3];
  wd1r = 1;
  wd1i = 0;
  wd3r = 1;
  wd3i = 0;
  k = 0;
  for (j = 2; j < mh - 2; j += 4) {
    k += 4;
    wk1r = csc1 * (wd1r + w[k]);
    wk1i = csc1 * (wd1i + w[k + 1]);
    wk3r = csc3 * (wd3r + w[k + 2]);
    wk3i = csc3 * (wd3i + w[k + 3]);
    wd1r = w[k];
    wd1i = w[k + 1];
    wd3r = w[k + 2];
    wd3i = w[k + 3];
    j1 = j + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j] + a[j2];
    x0i = a[j + 1] + a[j2 + 1];
    x1r = a[j] - a[j2];
    x1i = a[j + 1] - a[j2 + 1];
    y0r = a[j + 2] + a[j2 + 2];
    y0i = a[j + 3] + a[j2 + 3];
    y1r = a[j + 2] - a[j2 + 2];
    y1i = a[j + 3] - a[j2 + 3];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    y2r = a[j1 + 2] + a[j3 + 2];
    y2i = a[j1 + 3] + a[j3 + 3];
    y3r = a[j1 + 2] - a[j3 + 2];
    y3i = a[j1 + 3] - a[j3 + 3];
    a[j] = x0r + x2r;
    a[j + 1] = x0i + x2i;
    a[j + 2] = y0r + y2r;
    a[j + 3] = y0i + y2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i - x2i;
    a[j1 + 2] = y0r - y2r;
    a[j1 + 3] = y0i - y2i;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j2] = wk1r * x0r - wk1i * x0i;
    a[j2 + 1] = wk1r * x0i + wk1i * x0r;
    x0r = y1r - y3i;
    x0i = y1i + y3r;
    a[j2 + 2] = wd1r * x0r - wd1i * x0i;
    a[j2 + 3] = wd1r * x0i + wd1i * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j3] = wk3r * x0r + wk3i * x0i;
    a[j3 + 1] = wk3r * x0i - wk3i * x0r;
    x0r = y1r + y3i;
    x0i = y1i - y3r;
    a[j3 + 2] = wd3r * x0r + wd3i * x0i;
    a[j3 + 3] = wd3r * x0i - wd3i * x0r;
    j0 = m - j;
    j1 = j0 + m;
    j2 = j1 + m;
    j3 = j2 + m;
    x0r = a[j0] + a[j2];
    x0i = a[j0 + 1] + a[j2 + 1];
    x1r = a[j0] - a[j2];
    x1i = a[j0 + 1] - a[j2 + 1];
    y0r = a[j0 - 2] + a[j2 - 2];
    y0i = a[j0 - 1] + a[j2 - 1];
    y1r = a[j0 - 2] - a[j2 - 2];
    y1i = a[j0 - 1] - a[j2 - 1];
    x2r = a[j1] + a[j3];
    x2i = a[j1 + 1] + a[j3 + 1];
    x3r = a[j1] - a[j3];
    x3i = a[j1 + 1] - a[j3 + 1];
    y2r = a[j1 - 2] + a[j3 - 2];
    y2i = a[j1 - 1] + a[j3 - 1];
    y3r = a[j1 - 2] - a[j3 - 2];
    y3i = a[j1 - 1] - a[j3 - 1];
    a[j0] = x0r + x2r;
    a[j0 + 1] = x0i + x2i;
    a[j0 - 2] = y0r + y2r;
    a[j0 - 1] = y0i + y2i;
    a[j1] = x0r - x2r;
    a[j1 + 1] = x0i - x2i;
    a[j1 - 2] = y0r - y2r;
    a[j1 - 1] = y0i - y2i;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j2] = wk1i * x0r - wk1r * x0i;
    a[j2 + 1] = wk1i * x0i + wk1r * x0r;
    x0r = y1r - y3i;
    x0i = y1i + y3r;
    a[j2 - 2] = wd1i * x0r - wd1r * x0i;
    a[j2 - 1] = wd1i * x0i + wd1r * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j3] = wk3i * x0r + wk3r * x0i;
    a[j3 + 1] = wk3i * x0i - wk3r * x0r;
    x0r = y1r + y3i;
    x0i = y1i - y3r;
    a[j3 - 2] = wd3i * x0r + wd3r * x0i;
    a[j3 - 1] = wd3i * x0i - wd3r * x0r;
  }
  wk1r = csc1 * (wd1r + wn4r);
  wk1i = csc1 * (wd1i + wn4r);
  wk3r = csc3 * (wd3r - wn4r);
  wk3i = csc3 * (wd3i - wn4r);
  j0 = mh;
  j1 = j0 + m;
  j2 = j1 + m;
  j3 = j2 + m;
  x0r = a[j0 - 2] + a[j2 - 2];
  x0i = a[j0 - 1] + a[j2 - 1];
  x1r = a[j0 - 2] - a[j2 - 2];
  x1i = a[j0 - 1] - a[j2 - 1];
  x2r = a[j1 - 2] + a[j3 - 2];
  x2i = a[j1 - 1] + a[j3 - 1];
  x3r = a[j1 - 2] - a[j3 - 2];
  x3i = a[j1 - 1] - a[j3 - 1];
  a[j0 - 2] = x0r + x2r;
  a[j0 - 1] = x0i + x2i;
  a[j1 - 2] = x0r - x2r;
  a[j1 - 1] = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  a[j2 - 2] = wk1r * x0r - wk1i * x0i;
  a[j2 - 1] = wk1r * x0i + wk1i * x0r;
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  a[j3 - 2] = wk3r * x0r + wk3i * x0i;
  a[j3 - 1] = wk3r * x0i - wk3i * x0r;
  x0r = a[j0] + a[j2];
  x0i = a[j0 + 1] + a[j2 + 1];
  x1r = a[j0] - a[j2];
  x1i = a[j0 + 1] - a[j2 + 1];
  x2r = a[j1] + a[j3];
  x2i = a[j1 + 1] + a[j3 + 1];
  x3r = a[j1] - a[j3];
  x3i = a[j1 + 1] - a[j3 + 1];
  a[j0] = x0r + x2r;
  a[j0 + 1] = x0i + x2i;
  a[j1] = x0r - x2r;
  a[j1 + 1] = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  a[j2] = wn4r * (x0r - x0i);
  a[j2 + 1] = wn4r * (x0i + x0r);
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  a[j3] = -wn4r * (x0r + x0i);
  a[j3 + 1] = -wn4r * (x0i - x0r);
  x0r = a[j0 + 2] + a[j2 + 2];
  x0i = a[j0 + 3] + a[j2 + 3];
  x1r = a[j0 + 2] - a[j2 + 2];
  x1i = a[j0 + 3] - a[j2 + 3];
  x2r = a[j1 + 2] + a[j3 + 2];
  x2i = a[j1 + 3] + a[j3 + 3];
  x3r = a[j1 + 2] - a[j3 + 2];
  x3i = a[j1 + 3] - a[j3 + 3];
  a[j0 + 2] = x0r + x2r;
  a[j0 + 3] = x0i + x2i;
  a[j1 + 2] = x0r - x2r;
  a[j1 + 3] = x0i - x2i;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  a[j2 + 2] = wk1i * x0r - wk1r * x0i;
  a[j2 + 3] = wk1i * x0i + wk1r * x0r;
  x0r = x1r + x3i;
  x0i = x1i - x3r;
  a[j3 + 2] = wk3i * x0r + wk3r * x0i;
  a[j3 + 3] = wk3i * x0i - wk3r * x0r;
}
extern "C" void bitrv208neg(double *a) {
  double x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i,
    x5r, x5i, x6r, x6i, x7r, x7i;

  x1r = a[2];
  x1i = a[3];
  x2r = a[4];
  x2i = a[5];
  x3r = a[6];
  x3i = a[7];
  x4r = a[8];
  x4i = a[9];
  x5r = a[10];
  x5i = a[11];
  x6r = a[12];
  x6i = a[13];
  x7r = a[14];
  x7i = a[15];
  a[2] = x7r;
  a[3] = x7i;
  a[4] = x3r;
  a[5] = x3i;
  a[6] = x5r;
  a[7] = x5i;
  a[8] = x1r;
  a[9] = x1i;
  a[10] = x6r;
  a[11] = x6i;
  a[12] = x2r;
  a[13] = x2i;
  a[14] = x4r;
  a[15] = x4i;
}
extern "C" void bitrv208(double *a) {
  double x1r, x1i, x3r, x3i, x4r, x4i, x6r, x6i;

  x1r = a[2];
  x1i = a[3];
  x3r = a[6];
  x3i = a[7];
  x4r = a[8];
  x4i = a[9];
  x6r = a[12];
  x6i = a[13];
  a[2] = x4r;
  a[3] = x4i;
  a[6] = x6r;
  a[7] = x6i;
  a[8] = x1r;
  a[9] = x1i;
  a[12] = x3r;
  a[13] = x3i;
}
extern "C" void bitrv216neg(double *a) {
  double x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i,
    x5r, x5i, x6r, x6i, x7r, x7i, x8r, x8i,
    x9r, x9i, x10r, x10i, x11r, x11i, x12r, x12i,
    x13r, x13i, x14r, x14i, x15r, x15i;

  x1r = a[2];
  x1i = a[3];
  x2r = a[4];
  x2i = a[5];
  x3r = a[6];
  x3i = a[7];
  x4r = a[8];
  x4i = a[9];
  x5r = a[10];
  x5i = a[11];
  x6r = a[12];
  x6i = a[13];
  x7r = a[14];
  x7i = a[15];
  x8r = a[16];
  x8i = a[17];
  x9r = a[18];
  x9i = a[19];
  x10r = a[20];
  x10i = a[21];
  x11r = a[22];
  x11i = a[23];
  x12r = a[24];
  x12i = a[25];
  x13r = a[26];
  x13i = a[27];
  x14r = a[28];
  x14i = a[29];
  x15r = a[30];
  x15i = a[31];
  a[2] = x15r;
  a[3] = x15i;
  a[4] = x7r;
  a[5] = x7i;
  a[6] = x11r;
  a[7] = x11i;
  a[8] = x3r;
  a[9] = x3i;
  a[10] = x13r;
  a[11] = x13i;
  a[12] = x5r;
  a[13] = x5i;
  a[14] = x9r;
  a[15] = x9i;
  a[16] = x1r;
  a[17] = x1i;
  a[18] = x14r;
  a[19] = x14i;
  a[20] = x6r;
  a[21] = x6i;
  a[22] = x10r;
  a[23] = x10i;
  a[24] = x2r;
  a[25] = x2i;
  a[26] = x12r;
  a[27] = x12i;
  a[28] = x4r;
  a[29] = x4i;
  a[30] = x8r;
  a[31] = x8i;
}
extern "C" void bitrv216(double *a) {
  double x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i,
    x5r, x5i, x7r, x7i, x8r, x8i, x10r, x10i,
    x11r, x11i, x12r, x12i, x13r, x13i, x14r, x14i;

  x1r = a[2];
  x1i = a[3];
  x2r = a[4];
  x2i = a[5];
  x3r = a[6];
  x3i = a[7];
  x4r = a[8];
  x4i = a[9];
  x5r = a[10];
  x5i = a[11];
  x7r = a[14];
  x7i = a[15];
  x8r = a[16];
  x8i = a[17];
  x10r = a[20];
  x10i = a[21];
  x11r = a[22];
  x11i = a[23];
  x12r = a[24];
  x12i = a[25];
  x13r = a[26];
  x13i = a[27];
  x14r = a[28];
  x14i = a[29];
  a[2] = x8r;
  a[3] = x8i;
  a[4] = x4r;
  a[5] = x4i;
  a[6] = x12r;
  a[7] = x12i;
  a[8] = x2r;
  a[9] = x2i;
  a[10] = x10r;
  a[11] = x10i;
  a[14] = x14r;
  a[15] = x14i;
  a[16] = x1r;
  a[17] = x1i;
  a[20] = x5r;
  a[21] = x5i;
  a[22] = x13r;
  a[23] = x13i;
  a[24] = x3r;
  a[25] = x3i;
  a[26] = x11r;
  a[27] = x11i;
  a[28] = x7r;
  a[29] = x7i;
}
extern "C" void bitrv2conj(int n, int *ip, double *a) {
  int j, j1, k, k1, l, m, nh, nm;
  double xr, xi, yr, yi;

  m = 1;
  for (l = n >> 2; l > 8; l >>= 2) {
    m <<= 1;
  }
  nh = n >> 1;
  nm = 4 * m;
  if (l == 8) {
    for (k = 0; k < m; k++) {
      for (j = 0; j < k; j++) {
        j1 = 4 * j + 2 * ip[m + k];
        k1 = 4 * k + 2 * ip[m + j];
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 -= nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nh;
        k1 += 2;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 += nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += 2;
        k1 += nh;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 -= nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nh;
        k1 -= 2;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 += nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
      }
      k1 = 4 * k + 2 * ip[m + k];
      j1 = k1 + 2;
      k1 += nh;
      a[j1 - 1] = -a[j1 - 1];
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      a[k1 + 3] = -a[k1 + 3];
      j1 += nm;
      k1 += 2 * nm;
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nm;
      k1 -= nm;
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 -= 2;
      k1 -= nh;
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nh + 2;
      k1 += nh + 2;
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 -= nh - nm;
      k1 += 2 * nm - 2;
      a[j1 - 1] = -a[j1 - 1];
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      a[k1 + 3] = -a[k1 + 3];
    }
  } else {
    for (k = 0; k < m; k++) {
      for (j = 0; j < k; j++) {
        j1 = 4 * j + ip[m + k];
        k1 = 4 * k + ip[m + j];
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nh;
        k1 += 2;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += 2;
        k1 += nh;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nh;
        k1 -= 2;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= nm;
        xr = a[j1];
        xi = -a[j1 + 1];
        yr = a[k1];
        yi = -a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
      }
      k1 = 4 * k + ip[m + k];
      j1 = k1 + 2;
      k1 += nh;
      a[j1 - 1] = -a[j1 - 1];
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      a[k1 + 3] = -a[k1 + 3];
      j1 += nm;
      k1 += nm;
      a[j1 - 1] = -a[j1 - 1];
      xr = a[j1];
      xi = -a[j1 + 1];
      yr = a[k1];
      yi = -a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      a[k1 + 3] = -a[k1 + 3];
    }
  }
}
extern "C" void bitrv2(int n, int *ip, double *a) {
  int j, j1, k, k1, l, m, nh, nm;
  double xr, xi, yr, yi;

  m = 1;
  for (l = n >> 2; l > 8; l >>= 2) {
    m <<= 1;
  }
  nh = n >> 1;
  nm = 4 * m;
  if (l == 8) {
    for (k = 0; k < m; k++) {
      for (j = 0; j < k; j++) {
        j1 = 4 * j + 2 * ip[m + k];
        k1 = 4 * k + 2 * ip[m + j];
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 -= nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nh;
        k1 += 2;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 += nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += 2;
        k1 += nh;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 -= nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nh;
        k1 -= 2;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 += nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= 2 * nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
      }
      k1 = 4 * k + 2 * ip[m + k];
      j1 = k1 + 2;
      k1 += nh;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nm;
      k1 += 2 * nm;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nm;
      k1 -= nm;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 -= 2;
      k1 -= nh;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nh + 2;
      k1 += nh + 2;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 -= nh - nm;
      k1 += 2 * nm - 2;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
    }
  } else {
    for (k = 0; k < m; k++) {
      for (j = 0; j < k; j++) {
        j1 = 4 * j + ip[m + k];
        k1 = 4 * k + ip[m + j];
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nh;
        k1 += 2;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += 2;
        k1 += nh;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 += nm;
        k1 += nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nh;
        k1 -= 2;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
        j1 -= nm;
        k1 -= nm;
        xr = a[j1];
        xi = a[j1 + 1];
        yr = a[k1];
        yi = a[k1 + 1];
        a[j1] = yr;
        a[j1 + 1] = yi;
        a[k1] = xr;
        a[k1 + 1] = xi;
      }
      k1 = 4 * k + ip[m + k];
      j1 = k1 + 2;
      k1 += nh;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += nm;
      k1 += nm;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
    }
  }
}

/***********************************************************/
/***********************************************************/
/***********************************************************/
/***********************************************************/
extern "C" int cfttree(int n, int j, int k, double *a, int nw, double *w) {
  void cftmdl1(int n, double *a, double *w);
  void cftmdl2(int n, double *a, double *w);
  int i, isplt, m;

  if ((k & 3) != 0) {
    isplt = k & 1;
    if (isplt != 0) {
      cftmdl1(n, &a[j - n], &w[nw - (n >> 1)]);
    } else {
      cftmdl2(n, &a[j - n], &w[nw - n]);
    }
  } else {
    m = n;
    for (i = k; (i & 3) == 0; i >>= 2) {
      m <<= 2;
    }
    isplt = i & 1;
    if (isplt != 0) {
      while (m > 128) {
        cftmdl1(m, &a[j - m], &w[nw - (m >> 1)]);
        m >>= 2;
      }
    } else {
      while (m > 128) {
        cftmdl2(m, &a[j - m], &w[nw - m]);
        m >>= 2;
      }
    }
  }
  return isplt;
}
extern "C" void cftfx41(int n, double *a, int nw, double *w) {
  void cftf161(double *a, double *w);
  void cftf162(double *a, double *w);
  void cftf081(double *a, double *w);
  void cftf082(double *a, double *w);

  if (n == 128) {
    cftf161(a, &w[nw - 8]);
    cftf162(&a[32], &w[nw - 32]);
    cftf161(&a[64], &w[nw - 8]);
    cftf161(&a[96], &w[nw - 8]);
  } else {
    cftf081(a, &w[nw - 8]);
    cftf082(&a[16], &w[nw - 8]);
    cftf081(&a[32], &w[nw - 8]);
    cftf081(&a[48], &w[nw - 8]);
  }
}
extern "C" void cftleaf(int n, int isplt, double *a, int nw, double *w) {
  void cftmdl1(int n, double *a, double *w);
  void cftmdl2(int n, double *a, double *w);
  void cftf161(double *a, double *w);
  void cftf162(double *a, double *w);
  void cftf081(double *a, double *w);
  void cftf082(double *a, double *w);

  if (n == 512) {
    cftmdl1(128, a, &w[nw - 64]);
    cftf161(a, &w[nw - 8]);
    cftf162(&a[32], &w[nw - 32]);
    cftf161(&a[64], &w[nw - 8]);
    cftf161(&a[96], &w[nw - 8]);
    cftmdl2(128, &a[128], &w[nw - 128]);
    cftf161(&a[128], &w[nw - 8]);
    cftf162(&a[160], &w[nw - 32]);
    cftf161(&a[192], &w[nw - 8]);
    cftf162(&a[224], &w[nw - 32]);
    cftmdl1(128, &a[256], &w[nw - 64]);
    cftf161(&a[256], &w[nw - 8]);
    cftf162(&a[288], &w[nw - 32]);
    cftf161(&a[320], &w[nw - 8]);
    cftf161(&a[352], &w[nw - 8]);
    if (isplt != 0) {
      cftmdl1(128, &a[384], &w[nw - 64]);
      cftf161(&a[480], &w[nw - 8]);
    } else {
      cftmdl2(128, &a[384], &w[nw - 128]);
      cftf162(&a[480], &w[nw - 32]);
    }
    cftf161(&a[384], &w[nw - 8]);
    cftf162(&a[416], &w[nw - 32]);
    cftf161(&a[448], &w[nw - 8]);
  } else {
    cftmdl1(64, a, &w[nw - 32]);
    cftf081(a, &w[nw - 8]);
    cftf082(&a[16], &w[nw - 8]);
    cftf081(&a[32], &w[nw - 8]);
    cftf081(&a[48], &w[nw - 8]);
    cftmdl2(64, &a[64], &w[nw - 64]);
    cftf081(&a[64], &w[nw - 8]);
    cftf082(&a[80], &w[nw - 8]);
    cftf081(&a[96], &w[nw - 8]);
    cftf082(&a[112], &w[nw - 8]);
    cftmdl1(64, &a[128], &w[nw - 32]);
    cftf081(&a[128], &w[nw - 8]);
    cftf082(&a[144], &w[nw - 8]);
    cftf081(&a[160], &w[nw - 8]);
    cftf081(&a[176], &w[nw - 8]);
    if (isplt != 0) {
      cftmdl1(64, &a[192], &w[nw - 32]);
      cftf081(&a[240], &w[nw - 8]);
    } else {
      cftmdl2(64, &a[192], &w[nw - 64]);
      cftf082(&a[240], &w[nw - 8]);
    }
    cftf081(&a[192], &w[nw - 8]);
    cftf082(&a[208], &w[nw - 8]);
    cftf081(&a[224], &w[nw - 8]);
  }
}
extern "C" void cftrec4(int n, double *a, int nw, double *w) {
  int cfttree(int n, int j, int k, double *a, int nw, double *w);
  void cftleaf(int n, int isplt, double *a, int nw, double *w);
  void cftmdl1(int n, double *a, double *w);
  int isplt, j, k, m;

  m = n;
  while (m > 512) {
    m >>= 2;
    cftmdl1(m, &a[n - m], &w[nw - (m >> 1)]);
  }
  cftleaf(m, 1, &a[n - m], nw, w);
  k = 0;
  for (j = n - m; j > 0; j -= m) {
    k++;
    isplt = cfttree(m, j, k, a, nw, w);
    cftleaf(m, isplt, &a[j - m], nw, w);
  }
}
extern "C" void cftfsub(int n, double *a, int *ip, int nw, double *w) {
  void bitrv2(int n, int *ip, double *a);
  void bitrv216(double *a);
  void bitrv208(double *a);
  void cftf1st(int n, double *a, double *w);
  void cftrec4(int n, double *a, int nw, double *w);
  void cftleaf(int n, int isplt, double *a, int nw, double *w);
  void cftfx41(int n, double *a, int nw, double *w);
  void cftf161(double *a, double *w);
  void cftf081(double *a, double *w);
  void cftf040(double *a);
  void cftx020(double *a);

  if (n > 8) {
    if (n > 32) {
      cftf1st(n, a, &w[nw - (n >> 2)]);
    if (n > 512) {
      cftrec4(n, a, nw, w);
    } else if (n > 128) {
      cftleaf(n, 1, a, nw, w);
    } else {
      cftfx41(n, a, nw, w);
    }
      bitrv2(n, ip, a);
    } else if (n == 32) {
      cftf161(a, &w[nw - 8]);
      bitrv216(a);
    } else {
      cftf081(a, w);
      bitrv208(a);
    }
  } else if (n == 8) {
    cftf040(a);
  } else if (n == 4) {
    cftx020(a);
  }
}
extern "C" void cftbsub(int n, double *a, int *ip, int nw, double *w) {
  void bitrv2conj(int n, int *ip, double *a);
  void bitrv216neg(double *a);
  void bitrv208neg(double *a);
  void cftb1st(int n, double *a, double *w);
  void cftrec4(int n, double *a, int nw, double *w);
  void cftleaf(int n, int isplt, double *a, int nw, double *w);
  void cftfx41(int n, double *a, int nw, double *w);
  void cftf161(double *a, double *w);
  void cftf081(double *a, double *w);
  void cftb040(double *a);
  void cftx020(double *a);

  if (n > 8) {
    if (n > 32) {
      cftb1st(n, a, &w[nw - (n >> 2)]);
      if (n > 512) {
        cftrec4(n, a, nw, w);
      } else if (n > 128) {
        cftleaf(n, 1, a, nw, w);
      } else {
        cftfx41(n, a, nw, w);
      }
      bitrv2conj(n, ip, a);
    } else if (n == 32) {
      cftf161(a, &w[nw - 8]);
      bitrv216neg(a);
    } else {
      cftf081(a, w);
      bitrv208neg(a);
    }
  } else if (n == 8) {
    cftb040(a);
  } else if (n == 4) {
    cftx020(a);
  }
}
extern "C" void cdft(int n, int isgn, double *a, int *ip, double *w) {
	void cftfsub(int n, double *a, int *ip, int nw, double *w);
	void cftbsub(int n, double *a, int *ip, int nw, double *w);
	int nw;
	nw = ip[0];
	if (isgn >= 0) {
		cftfsub(n, a, ip, nw, w);
	} else {
		cftbsub(n, a, ip, nw, w);
	}
}
extern "C" void rdft(int n, int isgn, double *a, int *ip, double *w) {
	void cftfsub(int n, double *a, int *ip, int nw, double *w);
	void cftbsub(int n, double *a, int *ip, int nw, double *w);
	void rftfsub(int n, double *a, int nc, double *c);
	void rftbsub(int n, double *a, int nc, double *c);
	double xi;

	int nw = ip[0];
	int nc = ip[1];

	if (isgn >= 0) {
		if (n > 4) {
			cftfsub(n, a, ip, nw, w);
			rftfsub(n, a, nc, w + nw);
		} else if (n == 4) {
			cftfsub(n, a, ip, nw, w);
		}
		xi = a[0] - a[1];
		a[0] += a[1];
		a[1] = xi;
	} else {
		a[1] = 0.5 * (a[0] - a[1]);
		a[0] -= a[1];
		if (n > 4) {
			rftbsub(n, a, nc, w + nw);
			cftbsub(n, a, ip, nw, w);
		} else if (n == 4) {
			cftbsub(n, a, ip, nw, w);
		}
	}
}
extern "C" void BackwardFFT(fft_plan p) {
	if (p.c_out == NULL) {  // c2r
		p.input[0] = p.c_in[0][0];
		p.input[1] = p.c_in[p.n / 2][0];
		for (int i = 1; i < p.n / 2; ++i) {
			p.input[i * 2]  = p.c_in[i][0];
			p.input[i * 2 + 1]  = p.c_in[i][1];
		}
		rdft(p.n, -1, p.input, p.ip, p.w);
		for (int i = 0; i < p.n; ++i) p.out[i] = p.input[i] * 2.0;
	} else {  // c2c
		for (int i = 0; i < p.n; ++i) {
			p.input[i * 2] = p.c_in[i][0];
			p.input[i * 2 + 1] = p.c_in[i][1];
		}
		cdft(p.n * 2, -1, p.input, p.ip, p.w);
		for (int i = 0; i < p.n; ++i) {
			p.c_out[i][0] = p.input[i * 2];
			p.c_out[i][1] = p.input[i * 2 + 1];
		}
	}
}
extern "C" void ForwardFFT(fft_plan p) {

	if (p.c_in == NULL) {  // r2c
		for (int i = 0; i < p.n; ++i) p.input[i] = p.in[i];
		rdft(p.n, 1, p.input, p.ip, p.w);
		p.c_out[0][0] = p.input[0];
		p.c_out[0][1] = 0.0;
		for (int i = 1; i < p.n / 2; ++i) {
			p.c_out[i][0] = p.input[i * 2];
			p.c_out[i][1] = p.input[i * 2 + 1];
		}
		p.c_out[p.n / 2][0] = p.input[1];
		p.c_out[p.n / 2][1] = 0.0;
	} else {  // c2c
		for (int i = 0; i < p.n; ++i) {
			p.input[i * 2] = p.c_in[i][0];
			p.input[i * 2 + 1] = p.c_in[i][1];
		}
		cdft(p.n * 2, 1, p.input, p.ip, p.w);
		for (int i = 0; i < p.n; ++i) {
			p.c_out[i][0]   = p.input[i * 2];
			p.c_out[i][1]   = p.input[i * 2 + 1];
		}
	}

}

extern "C" void makewt(int nw, int *ip, double *w) {

	void makeipt(int nw, int *ip);
	int j, nwh, nw0, nw1;
	double delta, wn4r, wk1r, wk1i, wk3r, wk3i;

	ip[0] = nw;
	ip[1] = 1;
	if (nw > 2) {
		nwh = nw >> 1;
		delta = atan(1.0) / nwh;
		wn4r = cos(delta * nwh);
		w[0] = 1;
		w[1] = wn4r;
		if (nwh == 4) {
			w[2] = cos(delta * 2);
			w[3] = sin(delta * 2);
		} else if (nwh > 4) {
			makeipt(nw, ip);
			w[2] = 0.5 / cos(delta * 2);
			w[3] = 0.5 / cos(delta * 6);
			for (j = 4; j < nwh; j += 4) {
				w[j] = cos(delta * j);
				w[j + 1] = sin(delta * j);
				w[j + 2] = cos(3 * delta * j);
				w[j + 3] = -sin(3 * delta * j);
			}
		}
		nw0 = 0;
		while (nwh > 2) {
			nw1 = nw0 + nwh;
			nwh >>= 1;
			w[nw1] = 1;
			w[nw1 + 1] = wn4r;
			if (nwh == 4) {
				wk1r = w[nw0 + 4];
				wk1i = w[nw0 + 5];
				w[nw1 + 2] = wk1r;
				w[nw1 + 3] = wk1i;
			} else if (nwh > 4) {
				wk1r = w[nw0 + 4];
				wk3r = w[nw0 + 6];
				w[nw1 + 2] = 0.5 / wk1r;
				w[nw1 + 3] = 0.5 / wk3r;
				for (j = 4; j < nwh; j += 4) {
					wk1r = w[nw0 + 2 * j];
					wk1i = w[nw0 + 2 * j + 1];
					wk3r = w[nw0 + 2 * j + 2];
					wk3i = w[nw0 + 2 * j + 3];
					w[nw1 + j] = wk1r;
					w[nw1 + j + 1] = wk1i;
					w[nw1 + j + 2] = wk3r;
					w[nw1 + j + 3] = wk3i;
				}
			}
			nw0 = nw1;
		}
	}
}

extern "C" fft_plan fft_plan_dft_r2c_1d(int n, double *in, fft_complex *out, unsigned int flags) {
	void makewt(int nw, int *ip, double *w);
	void makect(int nc, int *ip, double *c);

	fft_plan output = {0};
	output.n = n;
	output.in = in;
	output.c_in = NULL;
	output.out = NULL;
	output.c_out = out;
	output.sign = FFT_FORWARD;
	output.flags = flags;
	output.input = new double[n];
	output.ip = new int[n];
	output.w = new double[n * 5 / 4];

	output.ip[0] = 0;
	makewt(output.n >> 2, output.ip, output.w);
	makect(output.n >> 2, output.ip, output.w + (output.n >> 2));
	return output;
}
//-----------------------------------------------------------------------------
// ZeroCrossingEngine() calculates the zero crossing points from positive to
// negative. Thanks to Custom.Maid http://custom-made.seesaa.net/ (2012/8/19)
//-----------------------------------------------------------------------------
extern "C" int ZeroCrossingEngine(double *filtered_signal, int y_length, double fs,
    double *interval_locations, double *intervals) {
  int *negative_going_points = new int[y_length];

  for (int i = 0; i < y_length - 1; ++i)
    negative_going_points[i] =
      0.0 < filtered_signal[i] && filtered_signal[i + 1] <= 0.0 ? i + 1 : 0;
  negative_going_points[y_length - 1] = 0;

  int *edges = new int[y_length];
  int count = 0;
  for (int i = 0; i < y_length; ++i)
    if (negative_going_points[i] > 0)
      edges[count++] = negative_going_points[i];

  if (count < 2) {
    delete[] edges;
    delete[] negative_going_points;
    return 0;
  }

  double *fine_edges = new double[count];
  for (int i = 0; i < count; ++i)
    fine_edges[i] =
      edges[i] - filtered_signal[edges[i] - 1] /
      (filtered_signal[edges[i]] - filtered_signal[edges[i] - 1]);

  for (int i = 0; i < count - 1; ++i) {
    intervals[i] = fs / (fine_edges[i + 1] - fine_edges[i]);
    interval_locations[i] = (fine_edges[i] + fine_edges[i + 1]) / 2.0 / fs;
  }

  delete[] fine_edges;
  delete[] edges;
  delete[] negative_going_points;
  return count - 1;
}
//-----------------------------------------------------------------------------
// DestroyZeroCrossings() frees the memory of array in the struct
//-----------------------------------------------------------------------------
extern "C" void DestroyZeroCrossings(ZeroCrossings *zero_crossings) {
  delete[] zero_crossings->negative_interval_locations;
  delete[] zero_crossings->positive_interval_locations;
  delete[] zero_crossings->peak_interval_locations;
  delete[] zero_crossings->dip_interval_locations;
  delete[] zero_crossings->negative_intervals;
  delete[] zero_crossings->positive_intervals;
  delete[] zero_crossings->peak_intervals;
  delete[] zero_crossings->dip_intervals;
}

/***********************************************************/
/***********************************************************/
/***********************************************************/
/***********************************************************/
extern "C" bool CheckLoadedFile(double *x, int fs, int nbit, int x_length) {
	if (x == NULL) {
		printf("error: File not found.\n");
		getch();
		return false;
	}
	printf("File information\n");
	printf("Sampling : %d Hz %d Bit\n", fs, nbit);
	printf("Length %d [sample]\n", x_length);
	printf("Length %f [sec]\n", static_cast<double>(x_length) / fs);
	return true;
}
/***********************************************************/
extern "C" bool GetParameters(FILE *fp, int *fs, int *nbit, int *wav_length) {
	char data_check[5] = {0};
	data_check[4] = '\0';
	unsigned char for_int_number[4];
	fread(for_int_number, 1, 4, fp);
	*fs = 0;
	for (int i = 3; i >= 0; --i) *fs = *fs * 256 + for_int_number[i];
	// Quantization
	fseek(fp, 6, SEEK_CUR);
	fread(for_int_number, 1, 2, fp);
	*nbit = for_int_number[0];
	// Skip until "data" is found. 2011/03/28
	while (0 != fread(data_check, 1, 1, fp)) {
		if (data_check[0] == 'd') {
			fread(&data_check[1], 1, 3, fp);
			if (0 != strcmp(data_check, "data")) {
				fseek(fp, -3, SEEK_CUR);
			} else {
				break;
			}
		}
	}
	if (0 != strcmp(data_check, "data")) {
		printf("data error.\n");
		return false;
	}
	fread(for_int_number, 1, 4, fp);  // "data"
	*wav_length = 0;
	for (int i = 3; i >= 0; --i)
	*wav_length = *wav_length * 256 + for_int_number[i];
	*wav_length /= (*nbit / 8);

	return true;
}
extern "C" bool CheckHeader(FILE *fp) {
	char data_check[5];
	fread(data_check, 1, 4, fp);  // "RIFF"
	data_check[4] = '\0';
	if (0 != strcmp(data_check, "RIFF")) {
		printf("RIFF error.\n");
		return false;
	}
	fseek(fp, 4, SEEK_CUR);
	fread(data_check, 1, 4, fp);  // "WAVE"
	if (0 != strcmp(data_check, "WAVE")) {
		printf("WAVE error.\n");
		return false;
	}
	fread(data_check, 1, 4, fp);  // "fmt "
	if (0 != strcmp(data_check, "fmt ")) {
		printf("fmt error.\n");
		return false;
	}

	fread(data_check, 1, 4, fp);  // 1 0 0 0
	if (!(16 == data_check[0] && 0 == data_check[1] && 	0 == data_check[2] && 0 == data_check[3])) {
	printf("fmt (2) error.\n");
	return false;
	}
	fread(data_check, 1, 2, fp);  // 1 0
	if (!(1 == data_check[0] && 0 == data_check[1])) {
	printf("Format ID error.\n");
	return false;
	}
	fread(data_check, 1, 2, fp);  // 1 0
	if (!(1 == data_check[0] && 0 == data_check[1])) {
	printf("This function cannot support stereo file\n");
	return false;
	}
	return true;
}
extern "C" double * wavread(char* filename, int *fs, int *nbit, int *wav_length) {
	FILE *fp = fopen(filename, "rb");
	if (NULL == fp) {
		printf("File not found.\n");
		return NULL;
	}
	if (CheckHeader(fp) == false) {
		fclose(fp);
		return NULL;
	}
	if (GetParameters(fp, fs, nbit, wav_length) == false) {
		fclose(fp);
		return NULL;
	}
	double *waveform = new double[*wav_length];
	if (waveform == NULL) return NULL;
	int quantization_byte = *nbit / 8;
	double zero_line = pow(2.0, *nbit - 1);
	double tmp, sign_bias;
	unsigned char for_int_number[4];

	for (int i = 0; i < *wav_length; ++i) {
		sign_bias = tmp = 0.0;
		fread(for_int_number, 1, quantization_byte, fp);  // "data"
		if (for_int_number[quantization_byte-1] >= 128) {
			sign_bias = pow(2.0, *nbit - 1);
			for_int_number[quantization_byte - 1] = for_int_number[quantization_byte - 1] & 0x7F;
		}
		for (int j = quantization_byte - 1; j >= 0; --j)
		tmp = tmp * 256.0 + for_int_number[j];
		waveform[i] = (tmp - sign_bias) / zero_line;
	}
	fclose(fp);
	return waveform;
}


/***********************************************************/
extern "C" void histc(double *x, int x_length, double *edges, int edges_length,
    int *index) {
  int count = 1;

  int i = 0;
  for (; i < edges_length; ++i) {
    index[i] = 1;
    if (edges[i] >= x[0]) break;
  }
  for (; i < edges_length; ++i) {
    if (edges[i] < x[count]) {
      index[i] = count;
    } else {
      index[i--] = count++;
    }
    if (count == x_length) break;
  }
  count--;
  for (i++; i < edges_length; ++i) index[i] = count;
}
extern "C" void interp1(double *x, double *y, int x_length, double *xi, int xi_length,
    double *yi) {
  double *h = new double[x_length - 1];
  double *p = new double[xi_length];
  double *s = new double[xi_length];
  int *k = new int[xi_length];

  for (int i = 0; i < x_length - 1; ++i) h[i] = x[i + 1] - x[i];
  for (int i = 0; i < xi_length; ++i) {
    p[i] = i;
    k[i] = 0;
  }

  histc(x, x_length, xi, xi_length, k);

  for (int i = 0; i < xi_length; ++i)
    s[i] = (xi[i] - x[k[i] - 1]) / h[k[i] - 1];

  for (int i = 0; i < xi_length; ++i)
    yi[i] = y[k[i] - 1] + s[i] * (y[k[i]] - y[k[i] - 1]);

  delete[] k;
  delete[] s;
  delete[] p;
  delete[] h;
}
//-----------------------------------------------------------------------------
// GetF0CandidatesSub() calculates the f0 candidates and deviations.
// This is the sub-function of GetF0Candidates() and assumes the calculation.
//-----------------------------------------------------------------------------
extern "C" void GetF0CandidatesSub(double **interpolated_f0_set, int time_axis_length,
    double f0_floor, double f0_ceil, double boundary_f0,
    double *f0_candidates, double *f0_deviations) {
  for (int i = 0; i < time_axis_length; ++i) {
    f0_candidates[i] = (interpolated_f0_set[0][i] +
      interpolated_f0_set[1][i] + interpolated_f0_set[2][i] +
      interpolated_f0_set[3][i]) / 4.0;

    f0_deviations[i] = sqrt(((interpolated_f0_set[0][i] - f0_candidates[i]) *
      (interpolated_f0_set[0][i] - f0_candidates[i]) +
      (interpolated_f0_set[1][i] - f0_candidates[i]) *
      (interpolated_f0_set[1][i] - f0_candidates[i]) +
      (interpolated_f0_set[2][i] - f0_candidates[i]) *
      (interpolated_f0_set[2][i] - f0_candidates[i]) +
      (interpolated_f0_set[3][i] - f0_candidates[i]) *
      (interpolated_f0_set[3][i] - f0_candidates[i])) / 3.0);

    if (f0_candidates[i] > boundary_f0 ||
        f0_candidates[i] < boundary_f0 / 2.0 ||
        f0_candidates[i] > f0_ceil || f0_candidates[i] < f0_floor) {
      f0_candidates[i] = 0.0;
      f0_deviations[i] = world::kMaximumValue;
    }
  }
}
//-----------------------------------------------------------------------------
// GetF0Candidates() calculates the F0 candidates based on the zero-crossings.
// Calculation of F0 candidates is carried out in GetF0CandidatesSub().
//-----------------------------------------------------------------------------
extern "C" void GetF0Candidates(const ZeroCrossings *zero_crossings, double boundary_f0,
  double f0_floor, double f0_ceil, double *time_axis, int time_axis_length,
  double *f0_candidates, double *f0_deviations) {
  if (0 == CheckEvent(zero_crossings->number_of_negatives - 2) *
      CheckEvent(zero_crossings->number_of_positives - 2) *
      CheckEvent(zero_crossings->number_of_peaks - 2) *
      CheckEvent(zero_crossings->number_of_dips - 2)) {
    for (int i = 0; i < time_axis_length; ++i) {
      f0_deviations[i] = world::kMaximumValue;
      f0_candidates[i] = 0.0;
    }
    return;
  }

  double *interpolated_f0_set[4];
  for (int i = 0; i < 4; ++i)
    interpolated_f0_set[i] = new double[time_axis_length];

  interp1(zero_crossings->negative_interval_locations,
      zero_crossings->negative_intervals,
      zero_crossings->number_of_negatives,
      time_axis, time_axis_length, interpolated_f0_set[0]);
  interp1(zero_crossings->positive_interval_locations,
      zero_crossings->positive_intervals,
      zero_crossings->number_of_positives,
      time_axis, time_axis_length, interpolated_f0_set[1]);
  interp1(zero_crossings->peak_interval_locations,
      zero_crossings->peak_intervals, zero_crossings->number_of_peaks,
      time_axis, time_axis_length, interpolated_f0_set[2]);
  interp1(zero_crossings->dip_interval_locations,
      zero_crossings->dip_intervals, zero_crossings->number_of_dips,
      time_axis, time_axis_length, interpolated_f0_set[3]);

  GetF0CandidatesSub(interpolated_f0_set, time_axis_length, f0_floor,
      f0_ceil, boundary_f0, f0_candidates, f0_deviations);
  for (int i = 0; i < 4; ++i) delete[] interpolated_f0_set[i];
}
//-----------------------------------------------------------------------------
// DesignLowCutFilter() calculates the coefficients the filter.
//-----------------------------------------------------------------------------
extern "C" void DesignLowCutFilter(int N, int fft_size, double *low_cut_filter) {
  for (int i = 1; i <= N; ++i)
    low_cut_filter[i - 1] = 0.5 - 0.5 * cos(i * 2.0 * world::kPi / (N + 1));
  for (int i = N; i < fft_size; ++i) low_cut_filter[i] = 0.0;
  double sum_of_amplitude = 0.0;
  for (int i = 0; i < N; ++i) sum_of_amplitude += low_cut_filter[i];
  for (int i = 0; i < N; ++i)
    low_cut_filter[i] = -low_cut_filter[i] / sum_of_amplitude;
  for (int i = 0; i < (N - 1) / 2; ++i)
    low_cut_filter[fft_size - (N - 1) / 2 + i] = low_cut_filter[i];
  for (int i = 0; i < N; ++i)
    low_cut_filter[i] = low_cut_filter[i + (N - 1) / 2];
  low_cut_filter[0] += 1.0;
}
extern "C" void decimate(double *x, int x_length, int r, double *y) {
	const int kNFact = 9;
	double *tmp1 = new double[x_length + kNFact * 2];
	double *tmp2 = new double[x_length + kNFact * 2];

	for (int i = 0; i < kNFact; ++i) tmp1[i] = 2 * x[0] - x[kNFact - i];
	for (int i = kNFact; i < kNFact + x_length; ++i) tmp1[i] = x[i - kNFact];
	for (int i = kNFact + x_length; i < 2 * kNFact + x_length; ++i)
		tmp1[i] = 2 * x[x_length - 1] - x[x_length - 2 - (i - (kNFact + x_length))];

	FilterForDecimate(tmp1, 2 * kNFact + x_length, r, tmp2);
	for (int i = 0; i < 2 * kNFact + x_length; ++i)
		tmp1[i] = tmp2[2 * kNFact + x_length - i - 1];
	FilterForDecimate(tmp1, 2 * kNFact + x_length, r, tmp2);
	for (int i = 0; i < 2 * kNFact + x_length; ++i)
		tmp1[i] = tmp2[2 * kNFact + x_length - i - 1];

	int nout = x_length / r + 1;
	int nbeg = r - r * nout + x_length;

	int count = 0;
	for (int i = nbeg; i < x_length + kNFact; i += r)
		y[count++] = tmp1[i + kNFact - 1];

	delete[] tmp1;
	delete[] tmp2;
}



extern "C" void fft_execute(fft_plan p) {
	if (p.sign == FFT_FORWARD) {
		ForwardFFT(p);
	} else {  // ifft
		BackwardFFT(p);
	}
}
//-----------------------------------------------------------------------------
// GetDownsampledSignal() calculates the spectrum for estimation.
// This function carries out downsampling to speed up the estimation process
// and calculates the spectrum of the downsampled signal.
//-----------------------------------------------------------------------------
extern "C" void GetSpectrumForEstimation(double *x, int x_length, int y_length, double actual_fs, int fft_size, int decimation_ratio, fft_complex *y_spectrum) {

	double *y = new double[fft_size];

	// Downsampling
	if (decimation_ratio != 1) {
		decimate(x, x_length, decimation_ratio, y);
	} else {
		for (int i = 0; i < x_length; ++i) y[i] = x[i];
	}

	// Removal of the DC component (y = y - mean value of y)
	double mean_y = 0.0;
	for (int i = 0; i < y_length; ++i) mean_y += y[i];
	mean_y /= y_length;
	for (int i = 0; i < y_length; ++i) y[i] -= mean_y;
	for (int i = y_length; i < fft_size; ++i) y[i] = 0.0;

	fft_plan forwardFFT = fft_plan_dft_r2c_1d(fft_size, y, y_spectrum, FFT_ESTIMATE);
	fft_execute(forwardFFT);

	// Low cut filtering (from 0.1.4)
	int cutoff_in_sample = matlab_round(actual_fs / 50.0);  // Cutoff is 50.0 Hz
	DesignLowCutFilter(cutoff_in_sample * 2 + 1, fft_size, y);

	fft_complex *filter_spectrum = new fft_complex[fft_size];
	forwardFFT.c_out = filter_spectrum;
	fft_execute(forwardFFT);

	double tmp = 0;
	for (int i = 0; i <= fft_size / 2; ++i) {
		tmp = y_spectrum[i][0] * filter_spectrum[i][0] -
		y_spectrum[i][1] * filter_spectrum[i][1];
		y_spectrum[i][1] = y_spectrum[i][0] * filter_spectrum[i][1] +
		y_spectrum[i][1] * filter_spectrum[i][0];
		y_spectrum[i][0] = tmp;
	}
	fft_destroy_plan(forwardFFT);
	delete[] y;
	delete[] filter_spectrum;

}
//-----------------------------------------------------------------------------
// NuttallWindow() calculates the coefficients of Nuttall window whose length
// is y_length.
//-----------------------------------------------------------------------------
extern "C" void NuttallWindow(int y_length, double *y) {
  double tmp;
  for (int i = 0; i < y_length; ++i) {
    tmp  = (i + 1 - (y_length + 1) / 2.0) / (y_length + 1);
    y[i] = 0.355768 + 0.487396 * cos(2 * world::kPi * tmp) +
      0.144232 * cos(4.0 * world::kPi * tmp) +
      0.012604 * cos(6.0 * world::kPi * tmp);
  }
}
extern "C" fft_plan fft_plan_dft_c2r_1d(int n, fft_complex *in, double *out,
    unsigned int flags) {
  void makewt(int nw, int *ip, double *w);
  void makect(int nc, int *ip, double *c);

  fft_plan output = {0};
  output.n = n;
  output.in = NULL;
  output.c_in = in;
  output.out = out;
  output.c_out = NULL;
  output.sign = FFT_BACKWARD;
  output.flags = flags;
  output.input = new double[n];
  output.ip = new int[n];
  output.w = new double[n * 5 / 4];

  output.ip[0] = 0;
  makewt(output.n >> 2, output.ip, output.w);
  makect(output.n >> 2, output.ip, output.w + (output.n >> 2));
  return output;
}
//-----------------------------------------------------------------------------
// GetFilteredSignal() calculates the signal that is the convolution of the
// input signal and low-pass filter.
// This function is only used in RawEventByDio()
//-----------------------------------------------------------------------------
extern "C" void GetFilteredSignal(int half_average_length, int fft_size,
    fft_complex *y_spectrum, int y_length, double *filtered_signal) {
  double *low_pass_filter = new double[fft_size];
  // Nuttall window is used as a low-pass filter.
  // Cutoff frequency depends on the window length.
  NuttallWindow(half_average_length * 4, low_pass_filter);
  for (int i = half_average_length * 4; i < fft_size; ++i)
    low_pass_filter[i] = 0.0;

  fft_complex *low_pass_filter_spectrum = new fft_complex[fft_size];
  fft_plan forwardFFT = fft_plan_dft_r2c_1d(fft_size, low_pass_filter,
      low_pass_filter_spectrum, FFT_ESTIMATE);
  fft_execute(forwardFFT);

  // Convolution
  double tmp = y_spectrum[0][0] * low_pass_filter_spectrum[0][0] -
    y_spectrum[0][1] * low_pass_filter_spectrum[0][1];
  low_pass_filter_spectrum[0][1] =
    y_spectrum[0][0] * low_pass_filter_spectrum[0][1] +
    y_spectrum[0][1] * low_pass_filter_spectrum[0][0];
  low_pass_filter_spectrum[0][0] = tmp;
  for (int i = 1; i <= fft_size / 2; ++i) {
    tmp = y_spectrum[i][0] * low_pass_filter_spectrum[i][0] -
      y_spectrum[i][1] * low_pass_filter_spectrum[i][1];
    low_pass_filter_spectrum[i][1] =
      y_spectrum[i][0] * low_pass_filter_spectrum[i][1] +
      y_spectrum[i][1] * low_pass_filter_spectrum[i][0];
    low_pass_filter_spectrum[i][0] = tmp;
    low_pass_filter_spectrum[fft_size - i - 1][0] =
      low_pass_filter_spectrum[i][0];
    low_pass_filter_spectrum[fft_size - i - 1][1] =
      low_pass_filter_spectrum[i][1];
  }

  fft_plan inverseFFT = fft_plan_dft_c2r_1d(fft_size,
      low_pass_filter_spectrum, filtered_signal, FFT_ESTIMATE);
  fft_execute(inverseFFT);

  // Compensation of the delay.
  int index_bias = half_average_length * 2;
  for (int i = 0; i < y_length; ++i)
    filtered_signal[i] = filtered_signal[i + index_bias];

  fft_destroy_plan(inverseFFT);
  fft_destroy_plan(forwardFFT);
  delete[] low_pass_filter_spectrum;
  delete[] low_pass_filter;
}
//-----------------------------------------------------------------------------
// GetFourZeroCrossingIntervals() calculates four zero-crossing intervals.
// (1) Zero-crossing going from negative to positive.
// (2) Zero-crossing going from positive to negative.
// (3) Peak, and (4) dip. (3) and (4) are calculated from the zero-crossings of
// the differential of waveform.
//-----------------------------------------------------------------------------
extern "C" void GetFourZeroCrossingIntervals(double *filtered_signal, int y_length,
    double actual_fs, ZeroCrossings *zero_crossings) {
  // x_length / 4 (old version) is fixed at 2013/07/14
  const int kMaximumNumber = y_length;
  zero_crossings->negative_interval_locations = new double[kMaximumNumber];
  zero_crossings->positive_interval_locations = new double[kMaximumNumber];
  zero_crossings->peak_interval_locations = new double[kMaximumNumber];
  zero_crossings->dip_interval_locations = new double[kMaximumNumber];
  zero_crossings->negative_intervals = new double[kMaximumNumber];
  zero_crossings->positive_intervals = new double[kMaximumNumber];
  zero_crossings->peak_intervals = new double[kMaximumNumber];
  zero_crossings->dip_intervals = new double[kMaximumNumber];

  zero_crossings->number_of_negatives = ZeroCrossingEngine(filtered_signal,
      y_length, actual_fs, zero_crossings->negative_interval_locations,
      zero_crossings->negative_intervals);

  for (int i = 0; i < y_length; ++i) filtered_signal[i] = -filtered_signal[i];
  zero_crossings->number_of_positives = ZeroCrossingEngine(filtered_signal,
      y_length, actual_fs, zero_crossings->positive_interval_locations,
      zero_crossings->positive_intervals);

  for (int i = 0; i < y_length - 1; ++i) filtered_signal[i] =
    filtered_signal[i] - filtered_signal[i + 1];
  zero_crossings->number_of_peaks = ZeroCrossingEngine(filtered_signal,
      y_length - 1, actual_fs, zero_crossings->peak_interval_locations,
      zero_crossings->peak_intervals);

  for (int i = 0; i < y_length - 1; ++i)
    filtered_signal[i] = -filtered_signal[i];
  zero_crossings->number_of_dips = ZeroCrossingEngine(filtered_signal,
      y_length - 1, actual_fs, zero_crossings->dip_interval_locations,
      zero_crossings->dip_intervals);
}
//-----------------------------------------------------------------------------
// RawEventByDio() calculates the zero-crossings.
//-----------------------------------------------------------------------------
extern "C" void CalculateRawEvent(double boundary_f0, double fs, fft_complex *y_spectrum,
	int y_length, int fft_size, double f0_floor, double f0_ceil,
	double *time_axis, int time_axis_length, double *f0_deviations,
	double *f0_candidates) {

	double *filtered_signal = new double[fft_size];
	GetFilteredSignal(matlab_round(fs / boundary_f0 / 2.0), fft_size, y_spectrum, y_length, filtered_signal);

	ZeroCrossings zero_crossings = {0};
	GetFourZeroCrossingIntervals(filtered_signal, y_length, fs,
	&zero_crossings);

	GetF0Candidates(&zero_crossings, boundary_f0, f0_floor, f0_ceil,
	time_axis, time_axis_length, f0_candidates, f0_deviations);

	DestroyZeroCrossings(&zero_crossings);
	delete[] filtered_signal;

}

extern "C" void GetF0CandidateAndStabilityMap(double *boundary_f0_list,
    int number_of_bands, double actual_fs, int y_length,
    double *time_axis, int f0_length, fft_complex *y_spectrum,
    int fft_size, double f0_floor, double f0_ceil,
    double **f0_candidate_map, double **f0_stability_map) {

	double * f0_candidates = new double[f0_length];
	double * f0_deviations = new double[f0_length];

	// Calculation of the acoustics events (zero-crossing)
	for (int i = 0; i < number_of_bands; ++i) {
		CalculateRawEvent(boundary_f0_list[i], actual_fs, y_spectrum,
		y_length, fft_size, f0_floor, f0_ceil, time_axis, f0_length,
		f0_deviations, f0_candidates);
		for (int j = 0; j < f0_length; ++j) {
			// A way to avoid zero division
			f0_stability_map[i][j] = f0_deviations[j] /
			(f0_candidates[j] + world::kMySafeGuardMinimum);
			f0_candidate_map[i][j] = f0_candidates[j];
		}
	}
	delete[] f0_candidates;
	delete[] f0_deviations;

}
//-----------------------------------------------------------------------------
// GetBestF0Contour() calculates the best f0 contour based on stabilities of
// all candidates. The F0 whose stability is minimum is selected.
//-----------------------------------------------------------------------------
extern "C" void GetBestF0Contour(int f0_length, double **f0_candidate_map,
    double **f0_stability_map, int number_of_bands, double *best_f0_contour) {
  double tmp;
  for (int i = 0; i < f0_length; ++i) {
    tmp = f0_stability_map[0][i];
    best_f0_contour[i] = f0_candidate_map[0][i];
    for (int j = 1; j < number_of_bands; ++j) {
      if (tmp > f0_stability_map[j][i]) {
        tmp = f0_stability_map[j][i];
        best_f0_contour[i] = f0_candidate_map[j][i];
      }
    }
  }
}
//-----------------------------------------------------------------------------
// SelectOneF0() corrects the f0[current_index] based on
// f0[current_index + sign].
//-----------------------------------------------------------------------------
extern "C" double SelectBestF0(double current_f0, double past_f0, double **f0_candidates,
    int number_of_candidates, int target_index, double allowed_range) {
  double reference_f0 = (current_f0 * 3.0 - past_f0) / 2.0;

  double minimum_error = fabs(reference_f0 - f0_candidates[0][target_index]);
  double best_f0 = f0_candidates[0][target_index];

  double current_error;
  for (int i = 1; i < number_of_candidates; ++i) {
    current_error = fabs(reference_f0 - f0_candidates[i][target_index]);
    if (current_error < minimum_error) {
      minimum_error = current_error;
      best_f0 = f0_candidates[i][target_index];
    }
  }
  if (fabs(1.0 - best_f0 / reference_f0) > allowed_range)
    return 0.0;
  return best_f0;
}
//-----------------------------------------------------------------------------
// FixStep1() is the 1st step of the postprocessing.
// This function eliminates the unnatural change of f0 based on allowed_range.
//-----------------------------------------------------------------------------
extern "C" void FixStep1(double *best_f0_contour, int f0_length, int voice_range_minimum,
    double allowed_range, double *f0_step1) {
  double *f0_base = new double[f0_length];
  // Initialization
  for (int i = 0; i < voice_range_minimum; ++i) f0_base[i] = 0.0;
  for (int i = voice_range_minimum; i < f0_length - voice_range_minimum; ++i)
    f0_base[i] = best_f0_contour[i];
  for (int i = f0_length - voice_range_minimum; i < f0_length; ++i)
    f0_base[i] = 0.0;

  // Processing to prevent the jumping of f0
  for (int i = 0; i < voice_range_minimum; ++i) f0_step1[i] = 0.0;
  for (int i = voice_range_minimum; i < f0_length; ++i)
    f0_step1[i] = fabs((f0_base[i] - f0_base[i - 1]) /
    (world::kMySafeGuardMinimum + f0_base[i])) <
    allowed_range ? f0_base[i] : 0.0;

  delete[] f0_base;
}
//-----------------------------------------------------------------------------
// FixStep2() is the 2nd step of the postprocessing.
// This function eliminates the suspected f0 in the anlaut and auslaut.
//-----------------------------------------------------------------------------
extern "C" void FixStep2(double *f0_step1, int f0_length, int voice_range_minimum,
    double *f0_step2) {
  for (int i = 0; i < f0_length; ++i) f0_step2[i] = f0_step1[i];

  int center = (voice_range_minimum - 1) / 2;
  for (int i = center; i < f0_length - center; ++i) {
    for (int j = -center; j <= center; ++j) {
      if (f0_step1[i + j] == 0) {
        f0_step2[i] = 0.0;
        break;
      }
    }
  }
}
//-----------------------------------------------------------------------------
// FixStep3() is the 3rd step of the postprocessing.
// This function corrects the f0 candidates from backward to forward.
//-----------------------------------------------------------------------------
extern "C" void FixStep3(double *f0_step2, int f0_length, double **f0_candidates,
    int number_of_candidates, double allowed_range, int *negative_index,
    int negative_count, double *f0_step3) {
  for (int i = 0; i < f0_length; i++) f0_step3[i] = f0_step2[i];

  int limit;
  for (int i = 0; i < negative_count; ++i) {
    limit = i == negative_count - 1 ? f0_length - 1 : negative_index[i + 1];
    for (int j = negative_index[i]; j < limit; ++j) {
      f0_step3[j + 1] =
        SelectBestF0(f0_step3[j], f0_step3[j - 1], f0_candidates,
            number_of_candidates, j + 1, allowed_range);
      if (f0_step3[j + 1] == 0) break;
    }
  }
}
//-----------------------------------------------------------------------------
// BackwardCorrection() is the 4th step of the postprocessing.
// This function corrects the f0 candidates from forward to backward.
//-----------------------------------------------------------------------------
extern "C" void FixStep4(double *f0_step3, int f0_length, double **f0_candidates,
    int number_of_candidates, double allowed_range, int *positive_index,
    int positive_count, double *f0_step4) {
  for (int i = 0; i < f0_length; ++i) f0_step4[i] = f0_step3[i];

  int limit;
  for (int i = positive_count - 1; i >= 0; --i) {
    limit = i == 0 ? 1 : positive_index[i - 1];
    for (int j = positive_index[i]; j > limit; --j) {
      f0_step4[j - 1] =
        SelectBestF0(f0_step4[j], f0_step4[j + 1], f0_candidates,
            number_of_candidates, j - 1, allowed_range);
      if (f0_step4[j - 1] == 0) break;
    }
  }
}
//-----------------------------------------------------------------------------
// CountNumberOfVoicedSections() counts the number of voiced sections.
//-----------------------------------------------------------------------------
extern "C" void CountNumberOfVoicedSections(double *f0_step2, int f0_length,
    int *positive_index, int *negative_index, int *positive_count,
    int *negative_count) {
  *positive_count = *negative_count = 0;
  for (int i = 1; i < f0_length; ++i) {
    if (f0_step2[i] == 0 && f0_step2[i - 1] != 0) {
      negative_index[(*negative_count)++] = i - 1;
    } else {
      if (f0_step2[i - 1] == 0 && f0_step2[i] != 0)
        positive_index[(*positive_count)++] = i;
    }
  }
}

//-----------------------------------------------------------------------------
// FixF0Contour() calculates the definitive f0 contour based on all f0
// candidates. There are four steps.
//-----------------------------------------------------------------------------
extern "C" void FixF0Contour(double frame_period, int number_of_candidates,
    int fs, double **f0_candidates, double *best_f0_contour, int f0_length,
    double f0_floor, double *fixed_f0_contour) {
  // memo:
  // These are the tentative values. Optimization should be required.
  int voice_range_minimum =
    static_cast<int>(0.5 + 1000.0 / frame_period / f0_floor) * 2 + 1;
  double allowed_range = 0.02 * frame_period;

  double *f0_tmp1 = new double[f0_length];
  double *f0_tmp2 = new double[f0_length];

  FixStep1(best_f0_contour, f0_length, voice_range_minimum,
      allowed_range, f0_tmp1);
  FixStep2(f0_tmp1, f0_length, voice_range_minimum, f0_tmp2);

  int positive_count, negative_count;
  int *positive_index = new int[f0_length];
  int *negative_index = new int[f0_length];
  CountNumberOfVoicedSections(f0_tmp2, f0_length, positive_index,
      negative_index, &positive_count, &negative_count);
  FixStep3(f0_tmp2, f0_length, f0_candidates, number_of_candidates,
      allowed_range, negative_index, negative_count, f0_tmp1);
  FixStep4(f0_tmp1, f0_length, f0_candidates, number_of_candidates,
      allowed_range, positive_index, positive_count, fixed_f0_contour);

  delete[] f0_tmp1;
  delete[] f0_tmp2;
  delete[] positive_index;
  delete[] negative_index;
}
extern "C" void OriginalDio(double *x, int x_length, int fs, double frame_period, double f0_floor, double f0_ceil, double channels_in_octave, int speed, double *time_axis, double *f0) {

	int number_of_bands = 2 + static_cast<int>(log(f0_ceil / f0_floor) / world::kLog2 * channels_in_octave);
	double * boundary_f0_list = new double[number_of_bands];
	for (int i = 0; i < number_of_bands; ++i)
	boundary_f0_list[i] = f0_floor * pow(2.0, i / channels_in_octave);

	// normalization
	int decimation_ratio = MyMax(MyMin(speed, 12), 1);
	int y_length = (1 + static_cast<int>(x_length / decimation_ratio));
	double actual_fs = static_cast<double>(fs) / decimation_ratio;
	int fft_size = GetSuitableFFTSize(y_length + (4 * static_cast<int>(1.0 + actual_fs / boundary_f0_list[0] / 2.0)));

	// Calculation of the spectrum used for the f0 estimation
	fft_complex *y_spectrum = new fft_complex[fft_size];
	GetSpectrumForEstimation(x, x_length, y_length, actual_fs, fft_size, decimation_ratio, y_spectrum);

	// f0map represents all F0 candidates. We can modify them.
	double **f0_candidate_map = new double *[number_of_bands];
	double **f0_stability_map = new double *[number_of_bands];
	int f0_length = GetSamplesForDIO(fs, x_length, frame_period);
	for (int i = 0; i < number_of_bands; ++i) {
		f0_candidate_map[i] = new double[f0_length];
		f0_stability_map[i] = new double[f0_length];
	}

	for (int i = 0; i < f0_length; ++i)
	time_axis[i] = i * frame_period / 1000.0;

	GetF0CandidateAndStabilityMap(boundary_f0_list, number_of_bands,
	  actual_fs, y_length, time_axis, f0_length, y_spectrum,
	  fft_size, f0_floor, f0_ceil, f0_candidate_map, f0_stability_map);

	// Selection of the best value based on fundamental-ness.
	double *best_f0_contour = new double[f0_length];
	GetBestF0Contour(f0_length, f0_candidate_map, f0_stability_map,
	  number_of_bands, best_f0_contour);

	// Postprocessing to find the best f0-contour.
	FixF0Contour(frame_period, number_of_bands, fs, f0_candidate_map,
	  best_f0_contour, f0_length, f0_floor, f0);

	delete[] best_f0_contour;
	delete[] y_spectrum;
	for (int i = 0; i < number_of_bands; ++i) {
	delete[] f0_stability_map[i];
	delete[] f0_candidate_map[i];
	}
	delete[] f0_stability_map;
	delete[] f0_candidate_map;
	delete[] boundary_f0_list;

}
extern "C" void Dio(double *x, int x_length, int fs, const DioOption option, double *time_axis, double *f0) {
	OriginalDio(x, x_length, fs, option.frame_period, option.f0_floor, option.f0_ceil, option.channels_in_octave, option.speed, time_axis, f0);
}
extern "C" void InitializeDioOption(DioOption *option) {
	// You can change default parameters.
	option->channels_in_octave = 2.0;
	option->f0_ceil = 640.0;
	option->f0_floor = 80.0;
	option->frame_period = 5;

	// You can use from 1 to 12.
	// Default value is for the fs of 44.1 kHz.
	// The lower value you use, the better performance you can obtain.
	option->speed = 11;
}

//-----------------------------------------------------------------------------
// GetIndexRaw() calculates the temporal positions for windowing.
// Since the result includes negative value and the value that exceeds the
// length of the input signal, it must be modified appropriately.
//-----------------------------------------------------------------------------
extern "C" void GetIndexRaw(double current_time, double *base_time, int base_time_length,
    int fs, double center_location, int *index_raw) {
  for (int i = 0; i < base_time_length; ++i)
    index_raw[i] = matlab_round((current_time + base_time[i] +
        center_location) * fs);
}

//-----------------------------------------------------------------------------
// GetMainWindow() generates the window function.
//-----------------------------------------------------------------------------
extern "C" void GetMainWindow(double current_time, int *index_raw,
    int base_time_length, int fs, double center_location,
    double window_length_in_time, double *main_window) {
  double tmp = 0.0;
  for (int i = 0; i < base_time_length; ++i) {
    tmp = static_cast<double>(index_raw[i] - 1.0) /
      fs - current_time - center_location;
    main_window[i] = 0.42 +
      0.5 * cos(2.0 * world::kPi * tmp / window_length_in_time) +
      0.08 * cos(4.0 * world::kPi * tmp / window_length_in_time);
  }
}

//-----------------------------------------------------------------------------
// GetDiffWindow() generates the differentiated window.
// Diff means differential.
//-----------------------------------------------------------------------------
extern "C" void GetDiffWindow(double *main_window, int base_time_length,
    double *diff_window) {
  diff_window[0] = -main_window[1] / 2.0;
  for (int i = 1; i < base_time_length - 1; ++i)
    diff_window[i] = -(main_window[i + 1] - main_window[i - 1]) / 2.0;
  diff_window[base_time_length - 1] = main_window[base_time_length - 2] / 2.0;
}

//-----------------------------------------------------------------------------
// GetSpectra() calculates two spectra of the waveform windowed by windows
// (main window and diff window).
//-----------------------------------------------------------------------------
extern "C" void GetSpectra(double *x, int x_length, int fft_size, int *index_raw,
    double *main_window, double *diff_window, int base_time_length,
    ForwardRealFFT *forward_real_fft, fft_complex *main_spectrum,
    fft_complex *diff_spectrum) {
  int *index = new int[base_time_length];

  for (int i = 0; i < base_time_length; ++i)
    index[i] = MyMax(0, MyMin(x_length - 1, index_raw[i] - 1));
  for (int i = 0; i < base_time_length; ++i)
    forward_real_fft->waveform[i] = x[index[i]] * main_window[i];
  for (int i = base_time_length; i < fft_size; ++i)
    forward_real_fft->waveform[i] = 0.0;

  fft_execute(forward_real_fft->forward_fft);
  for (int i = 0; i <= fft_size / 2; ++i) {
    main_spectrum[i][0] = forward_real_fft->spectrum[i][0];
    main_spectrum[i][1] = -forward_real_fft->spectrum[i][1];
  }

  for (int i = 0; i < base_time_length; ++i)
    forward_real_fft->waveform[i] = x[index[i]] * diff_window[i];
  for (int i = base_time_length; i < fft_size; ++i)
    forward_real_fft->waveform[i] = 0.0;
  fft_execute(forward_real_fft->forward_fft);
  for (int i = 0; i <= fft_size / 2; ++i) {
    diff_spectrum[i][0] = forward_real_fft->spectrum[i][0];
    diff_spectrum[i][1] = -forward_real_fft->spectrum[i][1];
  }

  delete[] index;
}

//-----------------------------------------------------------------------------
// GetTentativeF0() calculates the F0 based on the instantaneous frequency.
// Calculated value is tentative because it is fixed as needed.
//-----------------------------------------------------------------------------
extern "C" double GetTentativeF0(double *power_spectrum, double *numerator_i,
    int fft_size, int fs, double f0_initial) {
  double power_list[6];
  double fixp_list[6];
  int index;
  for (int i = 0; i < 2; ++i) {
    index = matlab_round(f0_initial * fft_size / fs * (i + 1));

    fixp_list[i] = static_cast<double>(index) * fs / fft_size +
      numerator_i[index] / power_spectrum[index] * fs / 2.0 / world::kPi;
    power_list[i] = power_spectrum[index];
  }

  double tmp1 = 0.0;
  double tmp2 = 0.0;
  for (int i = 0; i < 2; ++i) {
    tmp1 += power_list[i] * fixp_list[i] / (i + 1);
    tmp2 += power_list[i];
  }
  f0_initial = tmp1 / tmp2;

  for (int i = 0; i < 6; ++i) {
    index = matlab_round(f0_initial * fft_size / fs * (i + 1));
    fixp_list[i] = static_cast<double>(index) * fs / fft_size +
      numerator_i[index] / power_spectrum[index] * fs / 2.0 / world::kPi;
    power_list[i] = sqrt(power_spectrum[index]);
  }
  for (int i = 0; i < 6; ++i) {
    tmp1 += power_list[i] * fixp_list[i];
    tmp2 += power_list[i] * (i + 1);
  }

  return tmp1 / tmp2;
}

//-----------------------------------------------------------------------------
// FFT, IFFT and minimum phase analysis
extern "C" void InitializeForwardRealFFT(int fft_size, ForwardRealFFT *forward_real_fft) {
  forward_real_fft->fft_size = fft_size;
  forward_real_fft->waveform = new double[fft_size];
  forward_real_fft->spectrum = new fft_complex[fft_size];
  forward_real_fft->forward_fft = fft_plan_dft_r2c_1d(fft_size,
      forward_real_fft->waveform, forward_real_fft->spectrum, FFT_ESTIMATE);
}
extern "C" void DestroyForwardRealFFT(ForwardRealFFT *forward_real_fft) {
  fft_destroy_plan(forward_real_fft->forward_fft);
  delete[] forward_real_fft->spectrum;
  delete[] forward_real_fft->waveform;
}
//-----------------------------------------------------------------------------
// GetMeanF0() calculates the instantaneous frequency.
//-----------------------------------------------------------------------------
extern "C" double GetMeanF0(double *x, int x_length, int fs, double current_time,
    double f0_initial, int fft_size, double window_length_in_time,
    double *base_time, int base_time_length) {
  ForwardRealFFT forward_real_fft = {0};
  InitializeForwardRealFFT(fft_size, &forward_real_fft);
  fft_complex *main_spectrum = new fft_complex[fft_size];
  fft_complex *diff_spectrum = new fft_complex[fft_size];

  double *power_spectrum = new double[fft_size];
  double *numerator_i = new double[fft_size];
  for (int i = 0; i <= fft_size / 2; ++i) {
    power_spectrum[i] = 0.0;
    numerator_i[i] = 0.0;
  }

  int *index_raw = new int[base_time_length];
  double *main_window = new double[base_time_length];
  double *diff_window = new double[base_time_length];

  double center_location_list[2];
  center_location_list[0] = (1.0 / 4.0 - 0.5) / f0_initial;
  center_location_list[1] = (3.0 / 4.0 - 0.5) / f0_initial;

  for (int i = 0; i < 2; i++) {
    GetIndexRaw(current_time, base_time, base_time_length, fs,
        center_location_list[i], index_raw);
    GetMainWindow(current_time, index_raw, base_time_length, fs,
        center_location_list[i], window_length_in_time, main_window);
    GetDiffWindow(main_window, base_time_length, diff_window);
    GetSpectra(x, x_length, fft_size, index_raw, main_window, diff_window,
        base_time_length, &forward_real_fft, main_spectrum, diff_spectrum);

    for (int j = 0; j <= fft_size / 2; ++j) {
      numerator_i[j] += main_spectrum[j][0] * diff_spectrum[j][1] -
        main_spectrum[j][1] * diff_spectrum[j][0];
      power_spectrum[j] += main_spectrum[j][0] * main_spectrum[j][0] +
        main_spectrum[j][1] * main_spectrum[j][1];
    }
  }

  double tentative_f0 = GetTentativeF0(power_spectrum, numerator_i,
      fft_size, fs, f0_initial);

  delete[] diff_spectrum;
  delete[] diff_window;
  delete[] main_window;
  delete[] index_raw;
  delete[] numerator_i;
  delete[] power_spectrum;
  delete[] main_spectrum;
  DestroyForwardRealFFT(&forward_real_fft);

  return tentative_f0;
}
//-----------------------------------------------------------------------------
// GetRefinedF0() fixes the F0 estimated by Dio(). This function uses
// instantaneous frequency.
//-----------------------------------------------------------------------------
extern "C" double GetRefinedF0(double *x, int x_length, int fs, double current_time,
    double current_f0) {
  if (current_f0 == 0.0)
    return 0.0;

  double f0_initial = MyMax(world::kFloorF0,
      MyMin(world::kCeilF0, current_f0));
  int half_window_length = static_cast<int>(3.0 * static_cast<double>(fs)
    / f0_initial / 2.0 + 1.0);
  double window_length_in_time = (2.0 *
    static_cast<double>(half_window_length) + 1) /
    static_cast<double>(fs);
  double *base_time = new double[half_window_length * 2 + 1];
  for (int i = 0; i < half_window_length * 2 + 1; i++) {
    base_time[i] = static_cast<double>(-half_window_length + i) / fs;
  }
  int fft_size = static_cast<int>(pow(2.0, 2.0 +
    static_cast<int>(log(half_window_length * 2.0 + 1.0) / world::kLog2)));

  double mean_f0 = GetMeanF0(x, x_length, fs, current_time,
      f0_initial, fft_size, window_length_in_time, base_time,
      half_window_length * 2 + 1);

  // If amount of correction is overlarge (20 %), initial F0 is employed.
  if (fabs(mean_f0 - f0_initial) / f0_initial > 0.2) mean_f0 = f0_initial;

  delete[] base_time;

  return mean_f0;
}

extern "C" void StoneMask(double *x, int x_length, int fs, double *time_axis, double *f0,
    int f0_length, double *refined_f0) {
  for (int i = 0; i < f0_length; i++)
    refined_f0[i] = GetRefinedF0(x, x_length, fs, time_axis[i], f0[i]);
}

extern "C" void F0Estimation(double *x, int x_length, int fs, int f0_length, double *f0, double *time_axis) {
	double *refined_f0 = new double[f0_length];
	DioOption option;  // WORLD 0.1.1 and later
	InitializeDioOption(&option);  // Initialize the option

	// modification of the option
	option.frame_period = FRAMEPERIOD;
	// Following value represents the ratio for downsampling.
	// The signal is downsampled to fs / speed Hz.
	option.speed = 12;
	option.f0_floor = 80.0;
	option.f0_ceil = 640.0;
	option.channels_in_octave = 2.0;

//	printf("\nAnalysis\n");
//	DWORD elapsed_time = timeGetTime();
	Dio(x, x_length, fs, option, time_axis, f0);
  
//  printf("DIO: %d [msec]\n", timeGetTime() - elapsed_time);

	// StoneMask is carried out to improve the estimation performance.
//	elapsed_time = timeGetTime();
	StoneMask(x, x_length, fs, time_axis, f0, f0_length, refined_f0);
//	printf("StoneMask: %d [msec]\n", timeGetTime() - elapsed_time);

	for (int i = 0; i < f0_length; ++i) f0[i] = refined_f0[i];

	delete[] refined_f0;

	return;
}

int main(int argc, char *argv[]) {
	if (argc != 2) {
		printf("error\n");
		return 0;
	}
	int fs, nbit, x_length;
	double *x = wavread(argv[1], &fs, &nbit, &x_length);
//	if (CheckLoadedFile(x, fs, nbit, x_length) == false) {
//		printf("error: File not found.\n");
//		return 0;
//	}
	// Allocate memories
	// The number of samples for F0
	int f0_length = GetSamplesForDIO(fs, x_length, FRAMEPERIOD);
  	double *f0 = new double[f0_length];
	double *time_axis = new double[f0_length];
	// FFT size for CheapTrick, PLATINUM and Aperiodicity
	int fft_size = GetFFTSizeForCheapTrick(fs);
	double **spectrogram = new double *[f0_length];
	double **residual_spectrogram = new double *[f0_length];
	double **aperiodicity = new double *[f0_length];  // for aperiodicity	

	for (int i = 0; i < f0_length; ++i) {
		spectrogram[i] = new double[fft_size / 2 + 1];
		residual_spectrogram[i] = new double[fft_size + 1];  // miss??
		aperiodicity[i] = new double[fft_size / 2 + 1];
	}

	// F0 estimation
	//-------------------------------
	F0Estimation(x, x_length, fs, f0_length, f0, time_axis);
  printf("fs=%d\n",fs);
  printf("x_length=%d\n", x_length);
  printf("F0_length:%d\n",f0_length);
  for(int i=0;i<f0_length;i+=10){
    printf("f0[%d]: %f\n", i, f0[i]);
  }

	return 0;

}