/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "cache.h"

#ifndef MEMSET
# define MEMSET 	android_memset32
#endif

#ifndef L
# define L(label)	.L##label
#endif

#ifndef ALIGN
# define ALIGN(n)	.p2align n
#endif

#ifndef cfi_startproc
# define cfi_startproc			.cfi_startproc
#endif

#ifndef cfi_endproc
# define cfi_endproc			.cfi_endproc
#endif

#ifndef cfi_rel_offset
# define cfi_rel_offset(reg, off)	.cfi_rel_offset reg, off
#endif

#ifndef cfi_restore
# define cfi_restore(reg)		.cfi_restore reg
#endif

#ifndef cfi_adjust_cfa_offset
# define cfi_adjust_cfa_offset(off)	.cfi_adjust_cfa_offset off
#endif

#ifndef ENTRY
# define ENTRY(name)			\
	.type name,  @function; 	\
	.globl name;			\
	.p2align 4;			\
name:					\
	cfi_startproc
#endif

#ifndef END
# define END(name)			\
	cfi_endproc;			\
	.size name, .-name
#endif

#define CFI_PUSH(REG)						\
  cfi_adjust_cfa_offset (4);					\
  cfi_rel_offset (REG, 0)

#define CFI_POP(REG)						\
  cfi_adjust_cfa_offset (-4);					\
  cfi_restore (REG)

#define PUSH(REG)	pushl REG; CFI_PUSH (REG)
#define POP(REG)	popl REG; CFI_POP (REG)

#ifdef USE_AS_BZERO32
# define DEST		PARMS
# define LEN		DEST+4
# define SETRTNVAL
#else
# define DEST		PARMS
# define DWDS		DEST+4
# define LEN		DWDS+4
# define SETRTNVAL	movl DEST(%esp), %eax
#endif

#if (defined SHARED || defined __PIC__)
# define ENTRANCE	PUSH (%ebx);
# define RETURN_END	POP (%ebx); ret
# define RETURN		RETURN_END; CFI_PUSH (%ebx)
# define PARMS		8		/* Preserve EBX.  */
# define JMPTBL(I, B)	I - B

/* Load an entry in a jump table into EBX and branch to it.  TABLE is a
   jump table with relative offsets.   */
# define BRANCH_TO_JMPTBL_ENTRY(TABLE)				\
    /* We first load PC into EBX.  */				\
    call	__x86.get_pc_thunk.bx;				\
    /* Get the address of the jump table.  */			\
    add		$(TABLE - .), %ebx;				\
    /* Get the entry and convert the relative offset to the	\
       absolute address.  */					\
    add		(%ebx,%ecx,4), %ebx;				\
    /* We loaded the jump table and adjuested EDX. Go.  */	\
    jmp		*%ebx

	.section	.gnu.linkonce.t.__x86.get_pc_thunk.bx,"ax",@progbits
	.globl	__x86.get_pc_thunk.bx
	.hidden	__x86.get_pc_thunk.bx
	ALIGN (4)
	.type	__x86.get_pc_thunk.bx,@function
__x86.get_pc_thunk.bx:
	movl	(%esp), %ebx
	ret
#else
# define ENTRANCE
# define RETURN_END	ret
# define RETURN		RETURN_END
# define PARMS		4
# define JMPTBL(I, B)	I

/* Branch to an entry in a jump table.  TABLE is a jump table with
   absolute offsets.  */
# define BRANCH_TO_JMPTBL_ENTRY(TABLE)				\
    jmp		*TABLE(,%ecx,4)
#endif

	.section .text.sse2,"ax",@progbits
	ALIGN (4)
ENTRY (MEMSET)
	ENTRANCE

	movl	LEN(%esp), %ecx
	shr     $2, %ecx
#ifdef USE_AS_BZERO32
	xor	%eax, %eax
#else
	mov	DWDS(%esp), %eax
	mov	%eax, %edx
#endif
	movl	DEST(%esp), %edx
	cmp	$16, %ecx
	jae	L(16dbwordsormore)

L(write_less16dbwords):
	lea	(%edx, %ecx, 4), %edx
	BRANCH_TO_JMPTBL_ENTRY (L(table_less16dbwords))

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_less16dbwords):
	.int	JMPTBL (L(write_0dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_1dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_2dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_3dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_4dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_5dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_6dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_7dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_8dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_9dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_10dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_11dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_12dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_13dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_14dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_15dbwords), L(table_less16dbwords))
	.popsection

	ALIGN (4)
L(write_15dbwords):
	movl	%eax, -60(%edx)
L(write_14dbwords):
	movl	%eax, -56(%edx)
L(write_13dbwords):
	movl	%eax, -52(%edx)
L(write_12dbwords):
	movl	%eax, -48(%edx)
L(write_11dbwords):
	movl	%eax, -44(%edx)
L(write_10dbwords):
	movl	%eax, -40(%edx)
L(write_9dbwords):
	movl	%eax, -36(%edx)
L(write_8dbwords):
	movl	%eax, -32(%edx)
L(write_7dbwords):
	movl	%eax, -28(%edx)
L(write_6dbwords):
	movl	%eax, -24(%edx)
L(write_5dbwords):
	movl	%eax, -20(%edx)
L(write_4dbwords):
	movl	%eax, -16(%edx)
L(write_3dbwords):
	movl	%eax, -12(%edx)
L(write_2dbwords):
	movl	%eax, -8(%edx)
L(write_1dbwords):
	movl	%eax, -4(%edx)
L(write_0dbwords):
	SETRTNVAL
	RETURN

	ALIGN (4)
L(16dbwordsormore):
	test	$3, %edx
	jz	L(aligned4bytes)
	mov	%eax, (%edx)
	mov	%eax, -4(%edx, %ecx, 4)
	sub	$1, %ecx
	rol	$24, %eax
	add	$1, %edx
	test	$3, %edx
	jz	L(aligned4bytes)
	ror	$8, %eax
	add	$1, %edx
	test	$3, %edx
	jz	L(aligned4bytes)
	ror	$8, %eax
	add	$1, %edx
L(aligned4bytes):
	shl	$2, %ecx

#ifdef USE_AS_BZERO32
	pxor	%xmm0, %xmm0
#else
	movd	%eax, %xmm0
	pshufd	$0, %xmm0, %xmm0
#endif
	testl	$0xf, %edx
	jz	L(aligned_16)
/* ECX > 32 and EDX is not 16 byte aligned.  */
L(not_aligned_16):
	movdqu	%xmm0, (%edx)
	movl	%edx, %eax
	and	$-16, %edx
	add	$16, %edx
	sub	%edx, %eax
	add	%eax, %ecx
	movd	%xmm0, %eax
	ALIGN (4)
L(aligned_16):
	cmp	$128, %ecx
	jae	L(128bytesormore)

L(aligned_16_less128bytes):
	add	%ecx, %edx
	shr	$2, %ecx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes))

	ALIGN (4)
L(128bytesormore):
#ifdef SHARED_CACHE_SIZE
	PUSH (%ebx)
	mov	$SHARED_CACHE_SIZE, %ebx
#else
# if (defined SHARED || defined __PIC__)
	call	__x86.get_pc_thunk.bx
	add	$_GLOBAL_OFFSET_TABLE_, %ebx
	mov	__x86_shared_cache_size@GOTOFF(%ebx), %ebx
# else
	PUSH (%ebx)
	mov	__x86_shared_cache_size, %ebx
# endif
#endif
	cmp	%ebx, %ecx
	jae	L(128bytesormore_nt_start)

#ifdef DATA_CACHE_SIZE
	POP (%ebx)
# define RESTORE_EBX_STATE CFI_PUSH (%ebx)
	cmp	$DATA_CACHE_SIZE, %ecx
#else
# if (defined SHARED || defined __PIC__)
#  define RESTORE_EBX_STATE
	call	__x86.get_pc_thunk.bx
	add	$_GLOBAL_OFFSET_TABLE_, %ebx
	cmp	__x86_data_cache_size@GOTOFF(%ebx), %ecx
# else
	POP (%ebx)
#  define RESTORE_EBX_STATE CFI_PUSH (%ebx)
	cmp	__x86_data_cache_size, %ecx
# endif
#endif

	jae	L(128bytes_L2_normal)
	subl	$128, %ecx
L(128bytesormore_normal):
	sub	$128, %ecx
	movdqa	%xmm0, (%edx)
	movdqa	%xmm0, 0x10(%edx)
	movdqa	%xmm0, 0x20(%edx)
	movdqa	%xmm0, 0x30(%edx)
	movdqa	%xmm0, 0x40(%edx)
	movdqa	%xmm0, 0x50(%edx)
	movdqa	%xmm0, 0x60(%edx)
	movdqa	%xmm0, 0x70(%edx)
	lea	128(%edx), %edx
	jb	L(128bytesless_normal)


	sub	$128, %ecx
	movdqa	%xmm0, (%edx)
	movdqa	%xmm0, 0x10(%edx)
	movdqa	%xmm0, 0x20(%edx)
	movdqa	%xmm0, 0x30(%edx)
	movdqa	%xmm0, 0x40(%edx)
	movdqa	%xmm0, 0x50(%edx)
	movdqa	%xmm0, 0x60(%edx)
	movdqa	%xmm0, 0x70(%edx)
	lea	128(%edx), %edx
	jae	L(128bytesormore_normal)

L(128bytesless_normal):
	lea	128(%ecx), %ecx
	add	%ecx, %edx
	shr	$2, %ecx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes))

	ALIGN (4)
L(128bytes_L2_normal):
	prefetcht0	0x380(%edx)
	prefetcht0	0x3c0(%edx)
	sub	$128, %ecx
	movdqa	%xmm0, (%edx)
	movaps	%xmm0, 0x10(%edx)
	movaps	%xmm0, 0x20(%edx)
	movaps	%xmm0, 0x30(%edx)
	movaps	%xmm0, 0x40(%edx)
	movaps	%xmm0, 0x50(%edx)
	movaps	%xmm0, 0x60(%edx)
	movaps	%xmm0, 0x70(%edx)
	add	$128, %edx
	cmp	$128, %ecx
	jae	L(128bytes_L2_normal)

L(128bytesless_L2_normal):
	add	%ecx, %edx
	shr	$2, %ecx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes))

	RESTORE_EBX_STATE
L(128bytesormore_nt_start):
	sub	%ebx, %ecx
	mov	%ebx, %eax
	and	$0x7f, %eax
	add	%eax, %ecx
	movd	%xmm0, %eax
	ALIGN (4)
L(128bytesormore_shared_cache_loop):
	prefetcht0	0x3c0(%edx)
	prefetcht0	0x380(%edx)
	sub	$0x80, %ebx
	movdqa	%xmm0, (%edx)
	movdqa	%xmm0, 0x10(%edx)
	movdqa	%xmm0, 0x20(%edx)
	movdqa	%xmm0, 0x30(%edx)
	movdqa	%xmm0, 0x40(%edx)
	movdqa	%xmm0, 0x50(%edx)
	movdqa	%xmm0, 0x60(%edx)
	movdqa	%xmm0, 0x70(%edx)
	add	$0x80, %edx
	cmp	$0x80, %ebx
	jae	L(128bytesormore_shared_cache_loop)
	cmp	$0x80, %ecx
	jb	L(shared_cache_loop_end)

	ALIGN (4)
L(128bytesormore_nt):
	sub	$0x80, %ecx
	movntdq	%xmm0, (%edx)
	movntdq	%xmm0, 0x10(%edx)
	movntdq	%xmm0, 0x20(%edx)
	movntdq	%xmm0, 0x30(%edx)
	movntdq	%xmm0, 0x40(%edx)
	movntdq	%xmm0, 0x50(%edx)
	movntdq	%xmm0, 0x60(%edx)
	movntdq	%xmm0, 0x70(%edx)
	add	$0x80, %edx
	cmp	$0x80, %ecx
	jae	L(128bytesormore_nt)
	sfence
L(shared_cache_loop_end):
#if defined DATA_CACHE_SIZE || !(defined SHARED || defined __PIC__)
	POP (%ebx)
#endif
	add	%ecx, %edx
	shr	$2, %ecx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes))

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_16_128bytes):
	.int	JMPTBL (L(aligned_16_0bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_4bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_8bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_12bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_16bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_20bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_24bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_28bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_32bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_36bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_40bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_44bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_48bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_52bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_56bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_60bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_64bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_68bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_72bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_76bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_80bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_84bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_88bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_92bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_96bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_100bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_104bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_108bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_112bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_116bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_120bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_124bytes), L(table_16_128bytes))
	.popsection

	ALIGN (4)
L(aligned_16_112bytes):
	movdqa	%xmm0, -112(%edx)
L(aligned_16_96bytes):
	movdqa	%xmm0, -96(%edx)
L(aligned_16_80bytes):
	movdqa	%xmm0, -80(%edx)
L(aligned_16_64bytes):
	movdqa	%xmm0, -64(%edx)
L(aligned_16_48bytes):
	movdqa	%xmm0, -48(%edx)
L(aligned_16_32bytes):
	movdqa	%xmm0, -32(%edx)
L(aligned_16_16bytes):
	movdqa	%xmm0, -16(%edx)
L(aligned_16_0bytes):
	SETRTNVAL
	RETURN

	ALIGN (4)
L(aligned_16_116bytes):
	movdqa	%xmm0, -116(%edx)
L(aligned_16_100bytes):
	movdqa	%xmm0, -100(%edx)
L(aligned_16_84bytes):
	movdqa	%xmm0, -84(%edx)
L(aligned_16_68bytes):
	movdqa	%xmm0, -68(%edx)
L(aligned_16_52bytes):
	movdqa	%xmm0, -52(%edx)
L(aligned_16_36bytes):
	movdqa	%xmm0, -36(%edx)
L(aligned_16_20bytes):
	movdqa	%xmm0, -20(%edx)
L(aligned_16_4bytes):
	movl	%eax, -4(%edx)
	SETRTNVAL
	RETURN

	ALIGN (4)
L(aligned_16_120bytes):
	movdqa	%xmm0, -120(%edx)
L(aligned_16_104bytes):
	movdqa	%xmm0, -104(%edx)
L(aligned_16_88bytes):
	movdqa	%xmm0, -88(%edx)
L(aligned_16_72bytes):
	movdqa	%xmm0, -72(%edx)
L(aligned_16_56bytes):
	movdqa	%xmm0, -56(%edx)
L(aligned_16_40bytes):
	movdqa	%xmm0, -40(%edx)
L(aligned_16_24bytes):
	movdqa	%xmm0, -24(%edx)
L(aligned_16_8bytes):
	movq	%xmm0, -8(%edx)
	SETRTNVAL
	RETURN

	ALIGN (4)
L(aligned_16_124bytes):
	movdqa	%xmm0, -124(%edx)
L(aligned_16_108bytes):
	movdqa	%xmm0, -108(%edx)
L(aligned_16_92bytes):
	movdqa	%xmm0, -92(%edx)
L(aligned_16_76bytes):
	movdqa	%xmm0, -76(%edx)
L(aligned_16_60bytes):
	movdqa	%xmm0, -60(%edx)
L(aligned_16_44bytes):
	movdqa	%xmm0, -44(%edx)
L(aligned_16_28bytes):
	movdqa	%xmm0, -28(%edx)
L(aligned_16_12bytes):
	movq	%xmm0, -12(%edx)
	movl	%eax, -4(%edx)
	SETRTNVAL
	RETURN

END (MEMSET)
