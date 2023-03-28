package org.example.framework;

import org.example.annotation.Autowirte;
import org.example.annotation.Component;
import org.example.annotation.Configuration;
import org.example.annotation.Service;

import javax.annotation.*;
import java.io.File;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Application {


    private Map<String, Object> singletonObjects = new HashMap<>();
    private Map<String, Object> earlySingletonObjects = new HashMap<>();
    private Map<String, Object> singletonFactories = new HashMap<>();

    private Map<Class<?>, List<String>> allBeanNamesByType = new HashMap<>();


    public List<BeanPostProcessor> beanPostProcessors = new ArrayList();

    public static Application start(Class aClass) throws Exception {
        return new Application().run(aClass);
    }


    private Application run(Class aClass) throws Exception {
        String packageName = aClass.getPackage().getName().replace(".", "/");
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        URL resource = contextClassLoader.getResource(packageName);
        String path = resource.getPath();
        path = path.substring(0, path.indexOf("/class"));
        List<Class> classList = getClassNameByFile(path);
        List<Object> configurationBean = constructor(classList.stream().filter(e -> e.isAnnotationPresent(Configuration.class)).collect(Collectors.toList()));
        configurationInit(configurationBean);
        List<Object> serviceBean = constructor(classList.stream().filter(e -> e.isAnnotationPresent(Service.class)).collect(Collectors.toList()));
        serviceInit(serviceBean);
        return this;
    }

    public <T> T getBean(String beanName) {
        if (!singletonObjects.containsKey(beanName)) {
            throw new RuntimeException("找不到bean:" + beanName);
        }
        return (T) singletonObjects.get(beanName);
    }

    public <T> T getBean(Class<T> aClass) {
        if (allBeanNamesByType.containsKey(aClass)) {
            List<String> list = allBeanNamesByType.get(aClass);
            if (list.size() == 1) {
                return getBean(list.iterator().next());
            } else {
                throw new RuntimeException("找到多个bean");
            }
        }
        String name = aClass.getSimpleName();
        Service service = aClass.getAnnotation(Service.class);
        if (Objects.isNull(service)) {
            throw new RuntimeException("找不到该类：" + aClass.getName());
        }
        if (Objects.nonNull(service.value()) && !service.value().isEmpty()) {
            name = service.value();
        }
        return getBean(name);
    }

    private Object getBeanCache(String name) throws Exception {
        if (singletonObjects.containsKey(name)) {
            return singletonObjects.get(name);
        }
        if (earlySingletonObjects.containsKey(name)) {
            return earlySingletonObjects.get(name);
        }
        return null;
    }

    private Object getBeanCache(String name, Class clazz) throws Exception {
        Object bean = getBeanCache(name);
        if (Objects.nonNull(bean)) {
            return bean;
        }
        return getBeanCache(clazz);
    }

    public Object getBeanCache(Class clazz) throws Exception {
        Object bean = getBeanCache(clazz.getSimpleName());
        if (Objects.nonNull(bean)) {
            return bean;
        }
        List<String> list = allBeanNamesByType.get(clazz);
        if (Objects.isNull(list)) {
            return null;
        }
        if (list.size() != 1) {
            throw new RuntimeException("找到多个Bean:" + clazz.getName());
        }
        return getBeanCache(allBeanNamesByType.get(clazz).iterator().next());
    }

    public String transformedBeanName(Class aclass) {
        if (aclass.isAnnotationPresent(Service.class)) {
            Service service = (Service) aclass.getAnnotation(Service.class);
            if (Objects.nonNull(service.value()) && !service.value().isEmpty()) {
                return service.value();
            }
        }
        return aclass.getSimpleName();
    }

    public void configurationInit(List<Object> beans) throws Exception {
        if (beans.isEmpty()) {
            return;
        }
        List<Object> result = new ArrayList<>();
        for (Object bean : beans) {
            String beanName = transformedBeanName(bean.getClass());
            if (autowirteInjection(bean)) {
                singletonObjects.put(transformedBeanName(bean.getClass()), bean);
                if (bean instanceof BeanPostProcessor) {
                    beanPostProcessors.add((BeanPostProcessor) bean);
                }
                earlySingletonObjects.remove(beanName);
            }else {
                earlySingletonObjects.put(beanName,bean);
                result.add(bean);
            }
        }
        if (!result.isEmpty()) {
            configurationInit(result);
        }

    }

    public void serviceInit(List<Object> serviceBeans) throws Exception {
        if (serviceBeans.isEmpty()) {
            return;
        }
        List<Object> resultBean = new ArrayList<>();
        for (Object serviceBean : serviceBeans) {
            String beanName = transformedBeanName(serviceBean.getClass());
            if(autowirteInjection(serviceBean)){
                singletonObjects.put(transformedBeanName(serviceBean.getClass()), serviceBean);
                earlySingletonObjects.remove(beanName);
            }else {
                earlySingletonObjects.put(beanName,serviceBean);
                resultBean.add(serviceBean);
            }
        }
        if (!resultBean.isEmpty()) {
            configurationInit(resultBean);
        }
    }


    public boolean autowirteInjection(Object bean) throws Exception {
        List<Field> fieldList = Stream.of(Arrays.asList(bean.getClass().getFields()), Arrays.asList(bean.getClass().getDeclaredFields()))
                .flatMap(Collection::stream)
                .filter(e -> e.isAnnotationPresent(Autowirte.class))
                .distinct()
                .collect(Collectors.toList());
        List<Method> methodList = Stream.of(Arrays.asList(bean.getClass().getMethods()), Arrays.asList(bean.getClass().getDeclaredMethods()))
                .flatMap(Collection::stream)
                .filter(e -> e.isAnnotationPresent(Autowirte.class))
                .distinct()
                .collect(Collectors.toList());
        for (Field field : fieldList) {
            Object paramBean = getBeanCache(field.getName(), field.getType());
            if (Objects.isNull(paramBean)) {
                return false;
            }
            field.setAccessible(true);
            field.set(bean, paramBean);
        }
        for (Method method : methodList) {
            List<Object> params = new ArrayList<>();
            Parameter[] parameters = method.getParameters();
            for (Parameter parameter : parameters) {
                Object parameterBean = getBeanCache(parameter.getName(), parameter.getType());
                if (Objects.isNull(parameterBean)) {
                    return false;
                }
                params.add(parameterBean);
            }
            method.setAccessible(true);
            method.invoke(bean, params.toArray());
        }
        return true;
    }

    public List<Object> constructor(List<Class> classList) throws Exception {
        List<Class> resultClass = new ArrayList<>();
        List<Object> beans = new ArrayList<>();
        for (Class classes : classList) {
            Constructor defaultConstructor = null;
            Constructor autowriteConstructor = null;
            Set<Constructor> constructorSet = new HashSet<>();
            constructorSet.addAll(Arrays.asList(classes.getConstructors()));
            constructorSet.addAll(Arrays.asList(classes.getDeclaredConstructors()));
            if (constructorSet.size() == 1) {
                defaultConstructor = constructorSet.iterator().next();
            }
            List<Constructor> constructorList = constructorSet.stream().filter(e -> e.isAnnotationPresent(Autowirte.class)).collect(Collectors.toList());
            if (constructorList.size() > 1) {
                throw new RuntimeException("存在多个autowrite" + classes.getSimpleName());
            }
            if (constructorList.size() == 1) {
                autowriteConstructor = constructorList.get(0);
            }
            Constructor constructor = null;

            if (Objects.nonNull(autowriteConstructor)) {
                constructor = autowriteConstructor;
            } else if (Objects.nonNull(defaultConstructor)) {
                constructor = defaultConstructor;
            }
            Object objectBean = null;
            if (Objects.isNull(constructor)) {
                objectBean = classes.newInstance();
            } else {
                List<Object> params = new ArrayList<>();
                boolean flag = true;
                for (Parameter parameter : constructor.getParameters()) {
                    Object bean = getBeanCache(parameter.getName(), parameter.getType());
                    if (Objects.isNull(bean)) {
                        flag = false;
                        break;
                    }
                    params.add(bean);
                }
                if (flag) {
                    constructor.setAccessible(true);
//                    objectBean = classes.getConstructor(constructor.getParameterTypes()).newInstance(params.toArray());
                    objectBean = constructor.newInstance(params.toArray());
                }
            }
            if (Objects.nonNull(objectBean)) {
                beans.add(objectBean);
            } else {
                resultClass.add(classes);
            }
        }
        if (!resultClass.isEmpty()) {
            beans.add(constructor(resultClass));
        }
        return beans;
    }


    public List<Class> getClassNameByFile(String path) throws ClassNotFoundException {
        List<Class> classList = new ArrayList<>();
        File file = new File(path);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (File listFile : file.listFiles()) {
            if (listFile.isDirectory()) {
                classList.addAll(getClassNameByFile(listFile.getPath()));
            } else {
                String childFilePath = listFile.getPath();
                if (childFilePath.endsWith(".class")) {
                    childFilePath = childFilePath.substring(childFilePath.indexOf("\\classes") + 9, childFilePath.lastIndexOf("."));
                    childFilePath = childFilePath.replace("\\", ".");
                    Class aClass = classLoader.loadClass(childFilePath);
                    if (!getAnnotation(aClass) || aClass.isAnnotation()) {
                        continue;
                    }
                    Class[] interfaces = aClass.getInterfaces();
                    for (Class anInterface : interfaces) {
                        if (!allBeanNamesByType.containsKey(anInterface.getName())) {
                            allBeanNamesByType.put(anInterface, Arrays.asList(aClass.getSimpleName()));
                        } else {
                            allBeanNamesByType.get(anInterface).add(aClass.getSimpleName());
                        }
                    }
                    classList.add(classLoader.loadClass(childFilePath));
                }
            }
        }
        return classList;
    }

    public boolean getAnnotation(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == Deprecated.class ||
                    annotation.annotationType() == SuppressWarnings.class ||
                    annotation.annotationType() == Override.class ||
                    annotation.annotationType() == PostConstruct.class ||
                    annotation.annotationType() == PreDestroy.class ||
                    annotation.annotationType() == Resource.class ||
                    annotation.annotationType() == Resources.class ||
                    annotation.annotationType() == Generated.class ||
                    annotation.annotationType() == Target.class ||
                    annotation.annotationType() == Retention.class ||
                    annotation.annotationType() == Documented.class ||
                    annotation.annotationType() == Inherited.class) {
                continue;
            }
            if (annotation.annotationType() == Component.class) {
                return true;
            } else {
                return getAnnotation(annotation.annotationType());
            }
        }
        return false;
    }
}
