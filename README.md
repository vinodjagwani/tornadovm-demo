# TornadoVM Beginner Demo

A tiny Java project that runs two loops on your GPU with [TornadoVM](https://github.com/beehive-lab/TornadoVM) and checks the answers.

| Step | Annotation | What it does |
|---|---|---|
| 1 | `@Parallel` | Adds two arrays on the GPU (`c = a + b`) |
| 2 | `@Reduce` | Sums the numbers 1 to 1000 on the GPU (answer: 500500) |

TornadoVM 8.1.0 has only these two annotations. The code in [`GpuDemo.java`](src/main/java/demo/GpuDemo.java) is commented line by line for beginners.

## Requirements

- **JDK 25** (the project compiles with `release 25` and uses a package-private `static void main`)
- **Maven** 3.9+
- **A GPU driver with OpenCL** (Intel, AMD and NVIDIA drivers include it). No TornadoVM SDK install is needed: Maven downloads the TornadoVM jars from Maven Central.

## Run it

```bash
mvn -q -B compile exec:exec
```

Expected output:

```
=== Step 1: @Parallel  (c = a + b) ===
a = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
b = [10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0]
c = [11.0, 11.0, 11.0, 11.0, 11.0, 11.0, 11.0, 11.0, 11.0, 11.0]   (every item should be 11)
=== Step 2: @Reduce  (sum of 1 + 2 + ... + 1000) ===
GPU sum      = 500500.0
Expected sum = 500500.0
PASS - GPU answers are correct
```

The program exits with code 1 if any answer is wrong.

Maven prints some `WARNING` lines about restricted methods. They come from Maven's own jars and are harmless.

This output was produced on a laptop with Intel Iris Xe graphics (OpenCL backend), JDK 25 and TornadoVM 8.1.0.

## How it works

Every step follows the same three parts:

1. **Write a normal Java method.** Mark the loop with `@Parallel` (and the result parameter with `@Reduce` for sums).
2. **Put it in a `TaskGraph`.** Say what to copy to the GPU, which method to run, and what to copy back.
3. **Run it** with a `TornadoExecutionPlan`. TornadoVM compiles the method for the GPU at runtime.

## Use a different GPU backend

The `pom.xml` uses the OpenCL driver (`tornado-drivers-opencl`) and the flag `-Dtornado.enabled.backends=opencl`. For an NVIDIA GPU with the CUDA toolkit, the docs list `tornado-drivers-cuda` and the flag `cuda`. For Apple Silicon, use `tornado-drivers-metal` and `metal`.

## Troubleshooting

- **No device found or an OpenCL error:** update your graphics driver, then check that TornadoVM sees the GPU with `tornado --devices` (needs the TornadoVM SDK, see the [installation guide](https://tornadovm.readthedocs.io/en/latest/installation.html)).
- **`release version 25 not supported`:** you are running an older JDK. Switch to JDK 25 (check with `java -version`).

## Also in this folder

- [`tornadovm-article.md`](tornadovm-article.md): a short article explaining TornadoVM and jitLLM in plain English.

## Learn more

- [TornadoVM on GitHub](https://github.com/beehive-lab/TornadoVM) and its [official examples](https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples)
- [TornadoVM documentation](https://tornadovm.readthedocs.io/en/latest/)
- [jitLLM](https://github.com/beehive-lab/jitllm): run AI language models on the GPU in pure Java
