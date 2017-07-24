package org.slingerxv.limitart.funcs;

public class Funcs {

	public static <R> R invoke(Func<R> func) {
		return func == null ? null : func.run();
	}

	public static <T, R> R invoke(Func1<T, R> func, T t) {
		return func == null ? null : func.run(t);
	}

	public static <T1, T2, R> R invoke(Func2<T1, T2, R> func, T1 t1, T2 t2) {
		return func == null ? null : func.run(t1, t2);
	}

	public static <T1, T2, T3, R> R invoke(Func3<T1, T2, T3, R> func, T1 t1, T2 t2, T3 t3) {
		return func == null ? null : func.run(t1, t2, t3);
	}

	public static <T1, T2, T3, T4, R> R invoke(Func4<T1, T2, T3, T4, R> func, T1 t1, T2 t2, T3 t3, T4 t4) {
		return func == null ? null : func.run(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, T5, R> R invoke(Func5<T1, T2, T3, T4, T5, R> func, T1 t1, T2 t2, T3 t3, T4 t4,
			T5 t5) {
		return func == null ? null : func.run(t1, t2, t3, t4, t5);
	}
}
