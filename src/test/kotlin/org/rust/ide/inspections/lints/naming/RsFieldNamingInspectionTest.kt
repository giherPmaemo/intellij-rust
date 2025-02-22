/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsFieldNamingInspection

class RsFieldNamingInspectionTest : RsInspectionsTestBase(RsFieldNamingInspection::class) {
    fun `test enum variant fields`() = checkByText("""
        enum EnumVarFields {
            Variant {
                field_ok: u32,
                <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
            }
        }
    """)

    fun `test enum variant fields suppression`() = checkByText("""
        #[allow(non_snake_case)]
        enum EnumVarFields {
            Variant {
                FieldFoo: u32
            }
        }
    """)

    fun `test enum variant fields fix`() = checkFixByText("Rename to `field_foo`", """
        enum EnumToFix {
            Test {
                <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">Fi<caret>eldFoo</warning>: u32
            }
        }
        fn enum_use() {
            let mut a = EnumToFix::Test{ FieldFoo: 12 };
        }
    """, """
        enum EnumToFix {
            Test {
                field_foo: u32
            }
        }
        fn enum_use() {
            let mut a = EnumToFix::Test{ field_foo: 12 };
        }
    """, preview = null)

    fun `test struct fields`() = checkByText("""
        struct Foo {
            field_ok: u32,
            <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
        }
    """)

    fun `test struct fields suppression`() = checkByText("""
        #[allow(non_snake_case)]
        pub struct HoverParams {
            pub textDocument: Document,
            pub position: Position
        }
    """)

    fun `test struct fields fix`() = checkFixByText("Rename to `is_deleted`", """
        struct Foo {
            <warning descr="Field `IsDeleted` should have a snake case name such as `is_deleted`">IsDelete<caret>d</warning>: bool
        }
        fn struct_use() {
            let a = Foo { IsDeleted: false };
        }
    """, """
        struct Foo {
            is_deleted: bool
        }
        fn struct_use() {
            let a = Foo { is_deleted: false };
        }
    """, preview = null)

    fun `test enum variant field not support case`() = checkByText("""
        enum EnumVarField {
            Variant {
                字段: u32
            }
        }
        static 静态的: u32 = 12;
    """)

    fun `test struct field not support case`() = checkByText("""
        struct Foo {
            字段: u32
        }
    """)
}
