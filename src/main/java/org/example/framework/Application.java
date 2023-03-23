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
    private Map<String, Object> map = new HashMap<>();
    private Map<String, Object> L1cache = new HashMap();
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
        configurationInit(classList);
        serviceInit(classList);
        return this;
    }

    public <T> T getBean(String beanName) {
        if (!map.containsKey(beanName)) {
            throw new RuntimeException("找不到bean:" + beanName);
        }
        return (T) map.get(beanName);
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

    private Object getBeanCache(String name) {
        if (map.containsKey(name)) {
            return map.get(name);
        }
        if (L1cache.containsKey(name)) {
            return L1cache.get(name);
        }
        return null;
    }

    public Object getBeanCache(Class clazz) {
        String name = clazz.getSimpleName();
        if (map.containsKey(name)) {
            return map.get(name);
        }
        if (L1cache.containsKey(name)) {
            return L1cache.get(name);
        }
        List<String> list = allBeanNamesByType.get(clazz);
        if (Objects.isNull(list)) {
            return null;
        }
        if (list.size() != 1) {
            throw new RuntimeException("找到多个Bean:" + clazz.getName());
        }
        name = allBeanNamesByType.get(clazz).get(0);
        if (map.containsKey(name)) {
            return map.get(name);
        }
        if (L1cache.containsKey(name)) {
            return L1cache.get(name);
        }
        return null;
    }

    public void configurationInit(List<Class> classes) throws Exception {
        List<Class> cacheClass = new ArrayList<>();
        for (Class aClass : classes) {
            Configuration service = (Configuration) aClass.getAnnotation(Configuration.class);
            if (Objects.isNull(service)) {
                continue;
            }
            String key = aClass.getSimpleName();
            Object bean = constructor(aClass);
            if (Objects.isNull(bean)) {
                cacheClass.add(aClass);
                continue;
            }
            if (autowirteInjection(bean)) {
                map.put(key, bean);
                if (bean instanceof BeanPostProcessor) {
                    beanPostProcessors.add((BeanPostProcessor) bean);
                }
            } else {
                L1cache.put(key, bean);
            }
        }
        if (cacheClass.size() != 0) {
            configurationInit(cacheClass);
            return;
        }
        Iterator<Map.Entry<String, Object>> iterator = L1cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (autowirteInjection(entry.getValue())) {
                map.put(entry.getValue().getClass().getSimpleName(), entry.getValue());
                if (entry.getValue() instanceof BeanPostProcessor) {
                    beanPostProcessors.add((BeanPostProcessor) entry.getValue());
                }
                iterator.remove();
            }
        }
    }

    public void serviceInit(List<Class> classes) throws Exception {
        List<Class> cacheClass = new ArrayList<>();
        for (Class aClass : classes) {
            Service service = (Service) aClass.getAnnotation(Service.class);
            if (Objects.isNull(service)) {
                continue;
            }
            String key = aClass.getSimpleName();
            if (Objects.nonNull(service.value()) && !service.value().isEmpty()) {
                key = service.value();
            }
            Object bean = constructor(aClass);
            if (Objects.isNull(bean)) {
                cacheClass.add(aClass);
                continue;
            }
            if (autowirteInjection(bean)) {
                for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                    beanPostProcessor.postProcessBeforeInitialization(bean, key);
                }
                map.put(key, bean);
                for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                    beanPostProcessor.postProcessAfterInitialization(bean, key);
                }
            } else {
                L1cache.put(key, bean);
            }
        }
        if (cacheClass.size() != 0) {
            serviceInit(cacheClass);
            return;
        }
        Iterator<Map.Entry<String, Object>> iterator = L1cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (autowirteInjection(entry.getValue())) {
                Object bean = entry.getValue();
                String key = entry.getKey();
                for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                    bean = beanPostProcessor.postProcessBeforeInitialization(bean, key);
                }
                map.put(key, bean);
                for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                    beanPostProcessor.postProcessAfterInitialization(bean, key);
                }
                iterator.remove();
            }
        }
    }


    public boolean autowirteInjection(Object bean) throws IllegalAccessException, InvocationTargetException {
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
            Object paramBean = getBeanCache(field.getName());
            if (Objects.isNull(paramBean)) {
                paramBean = getBeanCache(field.getType());
            }
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
                Object parameterBean = getBeanCache(parameter.getName());
                if (Objects.isNull(parameterBean)) {
                    parameterBean = getBeanCache(parameter.getType());
                }
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

    public Object constructor(Class classes) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        Constructor defaultConstructor = null;
        Constructor autowriteConstructor = null;
        Set<Constructor> set = new HashSet<>();
        set.addAll(Arrays.asList(classes.getConstructors()));
        set.addAll(Arrays.asList(classes.getDeclaredConstructors()));
        if (set.size() == 1) {
            defaultConstructor = set.iterator().next();
        }
        for (Constructor constructor : set) {
            Annotation annotation = constructor.getAnnotation(Autowirte.class);
            if (Objects.nonNull(annotation)) {
                if (Objects.isNull(autowriteConstructor)) {
                    autowriteConstructor = constructor;
                } else {
                    throw new RuntimeException("存在多个autowrite" + classes.getSimpleName());
                }
            }
        }
        Constructor constructor;
        if (Objects.nonNull(autowriteConstructor)) {
            constructor = autowriteConstructor;
        } else if (Objects.nonNull(defaultConstructor)) {
            constructor = defaultConstructor;
        } else {
            return classes.newInstance();
        }
        List<Object> params = new ArrayList<>();
        for (Parameter parameter : constructor.getParameters()) {
            Object bean = getBeanCache(parameter.getName());
            if (Objects.isNull(bean)) {
                bean = getBeanCache(parameter.getType());
            }
            if (Objects.isNull(bean)) {
                return null;
            }
            params.add(bean);
        }
        constructor.setAccessible(true);
        return constructor.newInstance(params.toArray());
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
