````mermaid
flowchart TD
    subgraph LOCAL["本地开发环境"]
        Dev[👨‍💻 开发者写代码]
        Dev -->|Maven/Gradle Build| Jar[📦 Java App JAR]
        Jar -->|docker build| DockerImg[🐳 Docker Image: my-java-app:1.0]
    end

    subgraph K3D["本地 k3d 集群"]
        DockerImg -->|k3d image import| K3dNode[☸️ k3d Node Container]
        Helm[🎯 Helm Chart] --> K3dNode
        K3dNode --> Pod[🚀 Java Application Pod]
        Pod -->|NodePort/Port-Forward| Browser[🌐 浏览器访问]
    end

    subgraph FLOW["流程说明"]
        Dev -.->|提交代码触发 CI/CD| DockerImg
        Helm -.->|部署模板 + values.yaml| Pod
    end

