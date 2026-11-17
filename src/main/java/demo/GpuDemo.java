package demo;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Beginner demo for TornadoVM.
 *
 * <p><b>What is TornadoVM?</b> A tool that runs normal Java code on your graphics card (GPU).
 * A GPU has thousands of small workers, so it is very fast when the same small job must be done
 * on lots of numbers.
 *
 * <p><b>There are only two annotations to learn:</b>
 *
 * <ul>
 *   <li>{@code @Parallel} - "this loop can run all its rounds at the same time" (like many workers).</li>
 *   <li>{@code @Reduce}   - "many workers add into ONE result" (sum, max, min, ...).</li>
 * </ul>
 *
 * <p><b>Every step in this file follows the same 3 parts:</b>
 *
 * <ol>
 *   <li>Write a normal Java method (the "kernel": the work the GPU will do).</li>
 *   <li>Put it in a {@link TaskGraph}: what to copy to the GPU, what to run, what to copy back.</li>
 *   <li>Run the graph with a {@link TornadoExecutionPlan}. TornadoVM compiles your method for the GPU.</li>
 * </ol>
 *
 * <p>Read the file top to bottom: {@code main} -> Step 1 -> Step 2 -> small helpers.
 *
 * <p>More official examples (reductions, matrix multiply, N-body, ...):
 * https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples
 */
public final class GpuDemo {

    /** How many numbers Step 1 uses. Small on purpose, so you can read the printed arrays. */
    private static final int SIZE = 10;

    private GpuDemo() {
    }

    /**
     * Program start. Runs both steps, then prints PASS or FAIL.
     * "throws TornadoExecutionPlanException" is only there because running on the GPU can fail
     * (for example: no GPU driver found).
     */
    static void main(final String[] args) throws TornadoExecutionPlanException {
        final boolean step1Ok = step1AddTwoArrays();
        final boolean step2Ok = step2SumAnArray();
        printVerdict(step1Ok && step2Ok);
    }

    // ---------------------------------------------------------------- Step 1: @Parallel

    /**
     * THE KERNEL: the method that runs on the GPU.
     *
     * <p>It is a plain Java loop. The only new thing is {@code @Parallel} on the loop variable.
     * It tells TornadoVM: "round 0, round 1, round 2 ... do not depend on each other, so run them
     * all at the same time". On the GPU, each round becomes one small worker (thread).
     *
     * <p>That is true here because {@code c[i]} only needs {@code a[i]} and {@code b[i]}.
     * Never use {@code @Parallel} when one round needs the result of the previous round.
     *
     * <p>Note: this method must stay simple (loops and maths). TornadoVM translates it to GPU code,
     * and not every Java feature can be translated.
     */
    public static void addArrays(final float[] a, final float[] b, final float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    /** Prepares the data, runs the kernel on the GPU, prints the arrays, and checks the answer. */
    private static boolean step1AddTwoArrays() throws TornadoExecutionPlanException {
        System.out.println("=== Step 1: @Parallel  (c = a + b) ===");
        final float[] a = numbersFromOneUp();   // 1, 2, 3 ... 10
        final float[] b = numbersFromTenDown(); // 10, 9, 8 ... 1
        final float[] c = new float[SIZE];      // empty, the GPU will fill it

        runOnGpu(buildAddGraph(a, b, c));

        System.out.println("a = " + Arrays.toString(a));
        System.out.println("b = " + Arrays.toString(b));
        System.out.println("c = " + Arrays.toString(c) + "   (every item should be 11)");
        // 1 + 10 = 11, 2 + 9 = 11, ... so every item in c must be 11. If not, something is wrong.
        return allItemsEqual(c, 11f);
    }

    /**
     * Describes the job for the GPU. The GPU has its own memory, so we must say what to copy.
     * Nothing runs yet: this only builds the plan (the "TaskGraph").
     */
    private static TaskGraph buildAddGraph(final float[] a, final float[] b, final float[] c) {
        // "step1" is just a name for this graph. Pick any name.
        return new TaskGraph("step1")
                // 1) Copy inputs a and b from normal memory to the GPU.
                //    FIRST_EXECUTION = copy only the first time we run (inputs do not change).
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                // 2) Which Java method to run on the GPU. "add" is a name for this task.
                //    The arguments (a, b, c) are given to the method in the same order.
                .task("add", GpuDemo::addArrays, a, b, c)
                // 3) Copy the answer c back from the GPU to normal memory.
                //    EVERY_EXECUTION = copy back each time we run, so we always see the new answer.
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
    }

    // ---------------------------------------------------------------- Step 2: @Reduce

    /**
     * THE KERNEL for a "reduction": many numbers in, ONE number out (here: the sum).
     *
     * <p>Problem: if thousands of workers all add into the same {@code result} at the same time,
     * they would overwrite each other's work. {@code @Reduce} fixes this. It tells TornadoVM:
     * "{@code result} collects the answer". TornadoVM then makes each group of workers add up a part
     * and combines the parts safely at the end.
     *
     * <p>So: {@code @Reduce} goes on the PARAMETER that collects the answer,
     * and the loop is still marked {@code @Parallel}.
     *
     * <p>{@link FloatArray} is TornadoVM's own array type (stored outside the Java heap, so it
     * copies to the GPU efficiently). {@code result} is an array of size 1 that holds the sum.
     */
    public static void sumArray(final FloatArray input, @Reduce final FloatArray result) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    /** Prepares 1..1000, sums it on the GPU, and compares with the known answer. */
    private static boolean step2SumAnArray() throws TornadoExecutionPlanException {
        System.out.println("\n=== Step 2: @Reduce  (sum of 1 + 2 + ... + 1000) ===");
        final FloatArray input = oneToThousand();
        final FloatArray result = new FloatArray(1); // one slot for the sum, starts at 0

        runOnGpu(buildSumGraph(input, result));

        System.out.println("GPU sum      = " + result.get(0));
        System.out.println("Expected sum = 500500.0");
        // Maths trick: 1 + 2 + ... + n = n * (n + 1) / 2. For n = 1000 that is 500500.
        return result.get(0) == 500_500f;
    }

    /** Same 3 parts as Step 1: copy in, run, copy out. Here the input is copied on every run. */
    private static TaskGraph buildSumGraph(final FloatArray input, final FloatArray result) {
        return new TaskGraph("step2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("sum", GpuDemo::sumArray, input, result)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Runs a TaskGraph on the GPU.
     *
     * <p>{@code snapshot()} freezes the graph (it cannot change after this). The execution plan
     * compiles the Java kernel to GPU code the first time and runs it. The try-with-resources
     * block frees the GPU resources when we are done.
     */
    private static void runOnGpu(final TaskGraph graph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
    }

    /** Prints PASS or FAIL. On FAIL the program exits with code 1, so scripts can detect it. */
    private static void printVerdict(final boolean ok) {
        System.out.println("\n" + (ok ? "PASS - GPU answers are correct" : "FAIL - wrong answer"));
        if (!ok) {
            System.exit(1);
        }
    }

    /** True only when every item in the array equals the expected value. */
    private static boolean allItemsEqual(final float[] values, final float expected) {
        for (final float value : values) {
            if (value != expected) {
                return false;
            }
        }
        return true;
    }

    /** Makes the array [1, 2, 3, ... 10]. */
    private static float[] numbersFromOneUp() {
        final float[] data = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            data[i] = i + 1;
        }
        return data;
    }

    /** Makes the array [10, 9, 8, ... 1]. */
    private static float[] numbersFromTenDown() {
        final float[] data = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            data[i] = SIZE - i;
        }
        return data;
    }

    /** Makes a TornadoVM array holding 1, 2, 3 ... 1000. */
    private static FloatArray oneToThousand() {
        final FloatArray data = new FloatArray(1000);
        for (int i = 0; i < data.getSize(); i++) {
            data.set(i, i + 1);
        }
        return data;
    }
}
