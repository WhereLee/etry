package com.wherelee.material.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界回归测试。
 *
 * <p>这里用测试代替文档约定，防止后续开发重新把实现细节穿透到其他模块。</p>
 */
@AnalyzeClasses(packages = "com.wherelee.material")
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule platform_should_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("..modules.platform..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations..",
                            "..modules.material.."
                    );

    @ArchTest
    static final ArchRule identity_should_only_depend_on_platform =
            noClasses().that().resideInAPackage("..modules.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations..",
                            "..modules.material.."
                    );

    @ArchTest
    static final ArchRule learning_should_not_depend_on_other_business_modules =
            noClasses().that().resideInAPackage("..modules.learning..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.course..",
                            "..modules.commerce..",
                            "..modules.operations..",
                            "..modules.material.."
                    );

    @ArchTest
    static final ArchRule commerce_should_not_depend_on_operations =
            noClasses().that().resideInAPackage("..modules.commerce..")
                    .should().dependOnClassesThat().resideInAPackage("..modules.operations..");

    @ArchTest
    static final ArchRule commerce_should_not_depend_on_content_or_identity =
            noClasses().that().resideInAPackage("..modules.commerce..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.identity.."
                    )
                    .orShould().dependOnClassesThat(materialInternal());

    @ArchTest
    static final ArchRule material_should_only_depend_on_platform_and_apis =
            noClasses().that().resideInAPackage("..modules.material..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.operations.."
                    )
                    .orShould().dependOnClassesThat(identityInternal())
                    .orShould().dependOnClassesThat(commerceInternal())
                    .orShould().dependOnClassesThat(entitlementInternal());

    @ArchTest
    static final ArchRule operations_should_not_touch_material_internals =
            noClasses().that().resideInAPackage("..modules.operations..")
                    .should().dependOnClassesThat(materialInternal());

    @ArchTest
    static final ArchRule course_series_should_use_playback_contracts_not_implementations =
            noClasses().that().resideInAPackage("..modules.course.series..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.learning.playback..",
                            "..modules.platform.storage.PlaybackUrlCache"
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_commerce_repositories_or_compensation_services =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.commerce.order.OrderRepository",
                            "..modules.commerce.payment.PaymentRepository",
                            "..modules.commerce.payment.PaymentService",
                            "..modules.commerce.payment.RefundService",
                            "..modules.commerce.payment.OrderFulfillmentService"
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_commerce_internal_dtos =
            noClasses().that().resideInAPackage("..modules.operations..")
                    .should().dependOnClassesThat(commerceInternalDtos());

    @ArchTest
    static final ArchRule operations_should_not_touch_identity_internals =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity.user..",
                            "..modules.identity.security.."
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_course_governance_internals =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course.governance..",
                            "..modules.course.teacher..",
                            "..modules.course.series.."
                    );

    @ArchTest
    static final ArchRule entitlement_should_only_depend_on_platform =
            noClasses().that().resideInAPackage("..modules.entitlement..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations..",
                            "..modules.material.."
                    );

    @ArchTest
    static final ArchRule business_should_use_entitlement_api_not_internal =
            noClasses().that().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations..",
                            "..modules.material..")
                    .should().dependOnClassesThat(entitlementInternal());

    @ArchTest
    static final ArchRule course_learning_should_use_commerce_api_not_internal =
            noClasses().that().resideInAnyPackage("..modules.course..", "..modules.learning..")
                    .should().dependOnClassesThat(commerceInternal());

    private static DescribedPredicate<JavaClass> entitlementInternal() {
        return DescribedPredicate.describe("entitlement 内部实现（非 api）",
                javaClass -> javaClass.getPackageName().equals("com.wherelee.material.modules.entitlement"));
    }

    private static DescribedPredicate<JavaClass> commerceInternalDtos() {
        return DescribedPredicate.describe("commerce 内部 DTO（非 api 契约）",
                javaClass -> javaClass.getPackageName().equals("com.wherelee.material.modules.commerce.order.dto")
                        || javaClass.getName().startsWith("com.wherelee.material.modules.commerce.payment.PaymentDtos"));
    }

    private static DescribedPredicate<JavaClass> commerceInternal() {
        return DescribedPredicate.describe("commerce 内部实现（非 api）",
                javaClass -> javaClass.getPackageName().startsWith("com.wherelee.material.modules.commerce")
                        && !javaClass.getPackageName().startsWith("com.wherelee.material.modules.commerce.api"));
    }

    private static DescribedPredicate<JavaClass> identityInternal() {
        return DescribedPredicate.describe("identity 内部实现（非 api）",
                javaClass -> javaClass.getPackageName().startsWith("com.wherelee.material.modules.identity")
                        && !javaClass.getPackageName().startsWith("com.wherelee.material.modules.identity.api"));
    }

    private static DescribedPredicate<JavaClass> materialInternal() {
        return DescribedPredicate.describe("material 内部实现（非 api）",
                javaClass -> javaClass.getPackageName().startsWith("com.wherelee.material.modules.material")
                        && !javaClass.getPackageName().startsWith("com.wherelee.material.modules.material.api"));
    }
}