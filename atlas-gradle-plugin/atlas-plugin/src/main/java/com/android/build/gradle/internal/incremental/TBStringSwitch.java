package com.android.build.gradle.internal.incremental;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 创建日期：2019/4/10 on 下午6:39
 * 描述:
 * 作者:zhayu.ll
 */
public abstract class TBStringSwitch {

    static final Function<String, Integer> HASH_METHOD = String::hashCode;

        // Set this to a small positive number to force has hashcode collisions to exercise the
        // length and character checks.
        private static final Integer FORCE_HASH_COLLISION_MODULUS = null;

        // Figure out some types and methods ahead of time.
        private static final Type OBJECT_TYPE = Type.getType(java.lang.Object.class);
        private static final Type STRING_TYPE = Type.getType(java.lang.String.class);
        private static final Type ALI_INSTANT_RELOAD_EXCEPTION_TYPE =
                Type.getObjectType(TBIncrementalVisitor.ALI_RUNTIME_PACKAGE + "/InstantReloadException");

        // Methods overridden by caller to implement the switch behavior

        // Caller-implemented behavior should push one string onto the stack. This is the string to be
        // matched.
        abstract void visitString();

        // Caller-implemented behavior for when a string is matched. This method should return a value
        // appropriate for the method that it exists in or throw an exception.
        abstract void visitCase(String string);

        // Caller-implemented behavior to handle the default case of the switch. The default is an
        // error case so this method should throw an exception.
        abstract void visitDefault();

        // Override this method to provide a different hash generation method. You'll also need
        // to change HASH_METHOD in this class to correspond
        void visitHashMethod(GeneratorAdapter mv) {
            mv.invokeVirtual(STRING_TYPE, Method.getMethod("int hashCode()"));
        }

        /**
         * Emit code for a string if-else block.
         *
         *     if (s.equals("collided_method1")) {
         *         visit(s);
         *     } else if (s.equals("collided_method2")) {
         *         visit(s);
         *     }
         *
         * In the most common case of just one string, this degenerates to:
         *
         *      visit(s)
         *
         */
        private void visitx(GeneratorAdapter mv, List<String> strings) {
            if (strings.size() == 1) {
                visitCase(strings.get(0));
                return;
            }
            for (String string : strings) {
                Label label = new Label();
                visitString();
                mv.visitLdcInsn(string);
                mv.invokeVirtual(STRING_TYPE, Method.getMethod("boolean equals(Object)"));
                mv.visitJumpInsn(Opcodes.IFEQ, label);
                visitCase(string);
                mv.visitLabel(label);
            }

            visitDefault();
        }

        /**
         * Emit code for a string switch for the given string classifier.
         *
         * switch(s.hashCode()) {
         *   case 192: visitCase(s);
         *   case 312: visitCase(s);
         *   case 1024:
         *     if (s.equals("collided_method1")) {
         *         visit(s);
         *     } else if (s.equals("collided_method2")) {
         *         visit(s);
         *     }
         *     visitDefault();
         *   default:
         *     visitDefault();
         * }
         *
         **/
        private void visitClassifier(GeneratorAdapter mv, Set<String> strings) {
            visitString();
            visitHashMethod(mv);

            // Group strings by hash code.
            Multimap<Integer, String> buckets = Multimaps.index(strings, HASH_METHOD::apply);
            List<Map.Entry<Integer, Collection<String>>> sorted = Ordering.natural()
                    .onResultOf(Map.Entry<Integer, Collection<String>>::getKey)
                    .immutableSortedCopy(buckets.asMap().entrySet());

            int sortedHashes[] = new int[sorted.size()];
            List<String> sortedCases[] = new List[sorted.size()];
            int index = 0;
            for (Map.Entry<Integer, Collection<String>> entry : sorted) {
                sortedHashes[index] = entry.getKey();
                sortedCases[index] = Lists.newCopyOnWriteArrayList(entry.getValue());
                index++;
            }

            // Label for each hash and for default.
            Label labels[] = new Label[sorted.size()];
            Label defaultLabel = new Label();
            for (int i = 0; i < sorted.size(); ++i) {
                labels[i] = new Label();
            }

            // Create a switch that dispatches to each label from the hash code of
            mv.visitLookupSwitchInsn(defaultLabel, sortedHashes, labels);

            // Create the cases.
            for (int i = 0; i < sorted.size(); ++i) {
                mv.visitLabel(labels[i]);
                visitx(mv, sortedCases[i]);
            }
            mv.visitLabel(defaultLabel);
            visitDefault();
        }

        /**
         * Generates a standard error exception with message similar to:
         *
         *    String switch could not find 'equals.(Ljava/lang/Object;)Z' with hashcode 0
         *    in com/example/basic/GrandChild
         *
         * @param mv The generator adaptor used to emit the lookup switch code.
         * @param visitedClassName The abstract string trie structure.
         */
        void writeMissingMessageWithHash(GeneratorAdapter mv, String visitedClassName) {
            mv.newInstance(ALI_INSTANT_RELOAD_EXCEPTION_TYPE);
            mv.dup();
            mv.push("String switch could not find '%s' with hashcode %s in %s");
            mv.push(3);
            mv.newArray(OBJECT_TYPE);
            mv.dup();
            mv.push(0);
            visitString();
            mv.arrayStore(OBJECT_TYPE);
            mv.dup();
            mv.push(1);
            visitString();
            visitHashMethod(mv);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Integer",
                    "valueOf",
                    "(I)Ljava/lang/Integer;", false);
            mv.arrayStore(OBJECT_TYPE);
            mv.dup();
            mv.push(2);
            mv.push(visitedClassName);
            mv.arrayStore(OBJECT_TYPE);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/String",
                    "format",
                    "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
            mv.invokeConstructor(ALI_INSTANT_RELOAD_EXCEPTION_TYPE,
                    Method.getMethod("void <init> (String)"));
            mv.throwException();
        }

        /**
         * Main entry point for creation of string switch
         *
         * @param mv The generator adaptor used to emit the lookup switch code.
         * @param strings The closed set of strings to generate a switch for.
         */
        void visit(GeneratorAdapter mv, Set<String> strings) {
            visitClassifier(mv, strings);
        }
}
